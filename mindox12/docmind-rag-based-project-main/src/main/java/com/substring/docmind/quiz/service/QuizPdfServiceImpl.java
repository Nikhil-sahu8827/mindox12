package com.substring.docmind.quiz.service;

import com.substring.docmind.exception.ResourceNotFoundException;
import com.substring.docmind.quiz.dto.QuizPdfStatusDto;
import com.substring.docmind.quiz.entity.Quiz;
import com.substring.docmind.quiz.entity.QuizPdf;
import com.substring.docmind.quiz.entity.QuizQuestion;
import com.substring.docmind.quiz.repository.QuizPdfRepository;
import com.substring.docmind.quiz.repository.QuizQuestionRepository;
import com.substring.docmind.quiz.repository.QuizRepository;
import com.substring.docmind.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizPdfServiceImpl implements QuizPdfService {

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizPdfRepository quizPdfRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Color PRIMARY_COLOR = new Color(79, 70, 229); // Indigo 600
    private static final Color HEADER_BG = new Color(15, 23, 42); // Slate 900
    private static final Color TEXT_DARK = new Color(30, 41, 59); // Slate 800
    private static final Color TEXT_MUTED = new Color(100, 116, 139); // Slate 500
    private static final Color SUCCESS_GREEN = new Color(22, 101, 52); // Green 800
    private static final Color SUCCESS_BG = new Color(240, 253, 244); // Green 50
    private static final Color EXPLANATION_BG = new Color(248, 250, 252); // Slate 50
    private static final Color BORDER_COLOR = new Color(226, 232, 240); // Slate 200

    @Override
    @Transactional
    public QuizPdf generateOrUpdatePdf(String quizId) {
        log.info("Generating/Updating PDF document for quiz ID: {}", quizId);

        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", "id", quizId));

        List<QuizQuestion> allQuestions = quizQuestionRepository.findByQuizOrderByIdAsc(quiz);

        // Deduplicate questions by question text to ensure no duplicate entries
        List<QuizQuestion> cleanQuestions = deduplicateQuestions(allQuestions);

        try {
            byte[] pdfBytes = renderPdfDocument(quiz, cleanQuestions);
            String fileName = buildFileName(quiz);

            QuizPdf quizPdf = quizPdfRepository.findByQuizId(quizId)
                    .orElseGet(() -> QuizPdf.builder()
                            .quiz(quiz)
                            .fileName(fileName)
                            .build());

            quizPdf.setFileName(fileName);
            quizPdf.setTotalQuestions(cleanQuestions.size());
            quizPdf.setPdfData(pdfBytes);
            quizPdf.setFileSizeBytes((long) pdfBytes.length);

            QuizPdf saved = quizPdfRepository.save(quizPdf);
            log.info("Successfully synced Quiz PDF ({} questions, {} bytes) for quiz: {}",
                    cleanQuestions.size(), pdfBytes.length, quizId);
            return saved;
        } catch (Exception e) {
            log.error("Failed to generate PDF for quiz {}: {}", quizId, e.getMessage(), e);
            throw new RuntimeException("Could not generate quiz PDF: " + e.getMessage(), e);
        }
    }

    @Override
    @Async
    @Transactional
    public void generateOrUpdatePdfAsync(String quizId) {
        try {
            generateOrUpdatePdf(quizId);
        } catch (Exception e) {
            log.warn("Async PDF generation encountered error for quiz {}: {}", quizId, e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public QuizPdfStatusDto getPdfStatus(String quizId, UserPrincipal currentUser) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", "id", quizId));

        Optional<QuizPdf> pdfOpt = quizPdfRepository.findByQuizId(quizId);
        int totalQuestions = quizQuestionRepository.findByQuizOrderByIdAsc(quiz).size();

        return QuizPdfStatusDto.builder()
                .quizId(quizId)
                .fileName(pdfOpt.map(QuizPdf::getFileName).orElse(buildFileName(quiz)))
                .totalQuestions(pdfOpt.map(QuizPdf::getTotalQuestions).orElse(totalQuestions))
                .fileSizeBytes(pdfOpt.map(QuizPdf::getFileSizeBytes).orElse(0L))
                .available(pdfOpt.isPresent() && pdfOpt.get().getPdfData() != null)
                .lastUpdated(pdfOpt.map(QuizPdf::getUpdatedAt).orElse(null))
                .downloadUrl("/api/v1/quiz/" + quizId + "/pdf/download")
                .previewUrl("/api/v1/quiz/" + quizId + "/pdf/preview")
                .build();
    }

    @Override
    @Transactional
    public byte[] getPdfBytes(String quizId, UserPrincipal currentUser) {
        Optional<QuizPdf> pdfOpt = quizPdfRepository.findByQuizId(quizId);
        if (pdfOpt.isPresent() && pdfOpt.get().getPdfData() != null) {
            return pdfOpt.get().getPdfData();
        }

        // On-demand generation if not present in cache
        QuizPdf generated = generateOrUpdatePdf(quizId);
        return generated.getPdfData();
    }

    @Override
    public String getPdfFileName(String quizId) {
        return quizRepository.findById(quizId)
                .map(this::buildFileName)
                .orElse("DocMind_Quiz_" + quizId + ".pdf");
    }

    private String buildFileName(Quiz quiz) {
        String baseName = "Quiz";
        if (quiz.getDocument() != null && quiz.getDocument().getFilename() != null) {
            baseName = quiz.getDocument().getFilename()
                    .replaceAll("(?i)\\.(pdf|txt|docx|html|md|csv)$", "")
                    .replaceAll("[^a-zA-Z0-9_-]+", "_");
        } else if (quiz.getTitle() != null) {
            baseName = quiz.getTitle().replaceAll("[^a-zA-Z0-9_-]+", "_");
        }
        return "DocMind_" + baseName + ".pdf";
    }

    private List<QuizQuestion> deduplicateQuestions(List<QuizQuestion> questions) {
        List<QuizQuestion> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (QuizQuestion q : questions) {
            if (q == null || q.getQuestion() == null || q.getQuestion().isBlank()) {
                continue;
            }
            String norm = q.getQuestion().trim().toLowerCase();
            if (!seen.contains(norm)) {
                seen.add(norm);
                unique.add(q);
            }
        }
        return unique;
    }

    private byte[] renderPdfDocument(Quiz quiz, List<QuizQuestion> questions) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDType1Font fontTitle = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font fontOblique = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
            PDType1Font fontCourier = new PDType1Font(Standard14Fonts.FontName.COURIER);

            final float marginX = 40f;
            final float pageWidth = PDRectangle.A4.getWidth();
            final float pageHeight = PDRectangle.A4.getHeight();
            final float contentWidth = pageWidth - (2 * marginX);

            List<PDPage> pages = new ArrayList<>();
            PDPage currentPage = new PDPage(PDRectangle.A4);
            document.addPage(currentPage);
            pages.add(currentPage);

            PDPageContentStream cs = new PDPageContentStream(document, currentPage);
            float currentY = pageHeight - 40f;

            // Draw Top Banner on First Page
            currentY = drawHeaderBanner(cs, quiz, questions.size(), marginX, currentY, contentWidth, fontTitle,
                    fontBold, fontRegular);

            int qNum = 1;
            for (QuizQuestion q : questions) {
                // Estimate vertical space required for this question
                List<String> qLines = wrapText(q.getQuestion(), fontBold, 11f, contentWidth - 10f);
                List<String> codeLines = wrapCodeSnippet(q.getCodeSnippet(), fontCourier, 8.5f, contentWidth - 30f);
                List<List<String>> optLines = new ArrayList<>();
                if (q.getOptions() != null) {
                    for (String opt : q.getOptions()) {
                        optLines.add(wrapText(opt, fontRegular, 9.5f, contentWidth - 30f));
                    }
                }

                String correctText = "";
                if (q.getCorrectAnswer() != null && q.getOptions() != null
                        && q.getCorrectAnswer() >= 0 && q.getCorrectAnswer() < q.getOptions().size()) {
                    correctText = q.getOptions().get(q.getCorrectAnswer());
                }
                List<String> correctLines = wrapText("Correct Answer: " + correctText, fontBold, 9.5f,
                        contentWidth - 30f);

                String explanation = q.getExplanation() != null ? q.getExplanation() : "";
                List<String> expLines = wrapText("Explanation: " + explanation, fontRegular, 9f, contentWidth - 30f);

                int totalLines = qLines.size() + 2;
                if (!codeLines.isEmpty()) {
                    totalLines += codeLines.size() + 1;
                }
                for (List<String> ol : optLines)
                    totalLines += ol.size();
                totalLines += correctLines.size() + (!explanation.isBlank() ? expLines.size() + 1 : 0);

                float requiredHeight = (totalLines * 14f) + 40f;

                // Check page overflow
                if (currentY - requiredHeight < 65f) {
                    cs.close();
                    currentPage = new PDPage(PDRectangle.A4);
                    document.addPage(currentPage);
                    pages.add(currentPage);
                    cs = new PDPageContentStream(document, currentPage);
                    currentY = pageHeight - 50f;

                    // Draw running header on subsequent pages
                    currentY = drawRunningHeader(cs, quiz, marginX, currentY, contentWidth, fontBold, fontRegular);
                }

                currentY = drawQuestionBlock(cs, qNum++, q, qLines, codeLines, optLines, correctLines, expLines,
                        marginX, currentY, contentWidth, fontBold, fontRegular, fontOblique, fontCourier);

                currentY -= 15f; // gap between questions
            }

            cs.close();

            // Draw footers on all pages with actual total page count
            int totalPages = pages.size();
            for (int p = 0; p < totalPages; p++) {
                PDPage pg = pages.get(p);
                try (PDPageContentStream footerCs = new PDPageContentStream(document, pg,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    drawFooter(footerCs, p + 1, totalPages, marginX, 35f, contentWidth, fontRegular, fontOblique);
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    private float drawHeaderBanner(PDPageContentStream cs, Quiz quiz, int totalQuestions,
            float x, float y, float width,
            PDFont fontTitle, PDFont fontBold, PDFont fontRegular) throws Exception {
        float bannerHeight = 82f;
        float bannerY = y - bannerHeight;

        // Banner background
        cs.setNonStrokingColor(HEADER_BG);
        cs.addRect(x, bannerY, width, bannerHeight);
        cs.fill();

        // Top accent line (Indigo)
        cs.setNonStrokingColor(PRIMARY_COLOR);
        cs.addRect(x, y - 4f, width, 4f);
        cs.fill();

        // Main Brand Title
        cs.beginText();
        cs.setFont(fontTitle, 13f);
        cs.setNonStrokingColor(Color.WHITE);
        cs.newLineAtOffset(x + 16f, y - 24f);
        cs.showText("DOCMIND - AI KNOWLEDGE ASSESSMENT REPORT");
        cs.endText();

        // Quiz Title
        String titleText = sanitize(quiz.getTitle() != null ? quiz.getTitle() : "Dynamic Assessment");
        cs.beginText();
        cs.setFont(fontBold, 11f);
        cs.setNonStrokingColor(new Color(226, 232, 240));
        cs.newLineAtOffset(x + 16f, y - 42f);
        cs.showText(titleText.length() > 65 ? titleText.substring(0, 62) + "..." : titleText);
        cs.endText();

        // Metadata row
        String metaText = String.format(
                "Generated: %s   |   Total Questions: %d   |   Difficulty: %s   |   Status: Synchronized",
                DATE_FORMATTER
                        .format(quiz.getCreatedAt() != null ? quiz.getCreatedAt() : java.time.LocalDateTime.now()),
                totalQuestions,
                quiz.getDifficulty() != null ? quiz.getDifficulty().name() : "MIXED");

        cs.beginText();
        cs.setFont(fontRegular, 8.5f);
        cs.setNonStrokingColor(new Color(148, 163, 184));
        cs.newLineAtOffset(x + 16f, y - 62f);
        cs.showText(metaText);
        cs.endText();

        return bannerY - 20f;
    }

    private float drawRunningHeader(PDPageContentStream cs, Quiz quiz, float x, float y, float width,
            PDFont fontBold, PDFont fontRegular) throws Exception {
        cs.beginText();
        cs.setFont(fontRegular, 8.5f);
        cs.setNonStrokingColor(TEXT_MUTED);
        cs.newLineAtOffset(x, y);
        cs.showText("DocMind Quiz: " + sanitize(quiz.getTitle()));
        cs.endText();

        // Divider line
        cs.setStrokingColor(BORDER_COLOR);
        cs.setLineWidth(0.75f);
        cs.moveTo(x, y - 6f);
        cs.lineTo(x + width, y - 6f);
        cs.stroke();

        return y - 22f;
    }

    private float drawQuestionBlock(PDPageContentStream cs, int number, QuizQuestion q,
            List<String> qLines, List<String> codeLines, List<List<String>> optLines,
            List<String> correctLines, List<String> expLines,
            float x, float startY, float width,
            PDFont fontBold, PDFont fontRegular, PDFont fontOblique, PDFont fontCourier) throws Exception {
        float y = startY;

        // Question header: e.g. "Question 1 [CODE SNIPPETS]" (Badge / Accent)
        cs.setNonStrokingColor(PRIMARY_COLOR);
        cs.beginText();
        cs.setFont(fontBold, 10.5f);
        cs.newLineAtOffset(x, y);
        String typeLabel = (q.getType() != null && !q.getType().isBlank()) ? "  [" + q.getType().replace("_", " ") + "]"
                : "";
        cs.showText(String.format("Question %d%s", number, typeLabel));
        cs.endText();
        y -= 15f;

        // Question text lines
        cs.setNonStrokingColor(TEXT_DARK);
        for (String qLine : qLines) {
            cs.beginText();
            cs.setFont(fontBold, 10f);
            cs.newLineAtOffset(x + 4f, y);
            cs.showText(qLine);
            cs.endText();
            y -= 13.5f;
        }

        y -= 4f;

        // Code snippet block if present
        if (codeLines != null && !codeLines.isEmpty()) {
            float boxH = (codeLines.size() * 11.5f) + 8f;
            cs.setNonStrokingColor(new Color(241, 245, 249)); // Slate 100 bg
            cs.addRect(x + 6f, y - boxH + 6f, width - 12f, boxH);
            cs.fill();

            cs.setStrokingColor(new Color(203, 213, 225)); // Slate 300 border
            cs.setLineWidth(0.5f);
            cs.addRect(x + 6f, y - boxH + 6f, width - 12f, boxH);
            cs.stroke();

            for (String cLine : codeLines) {
                cs.beginText();
                cs.setFont(fontCourier, 8.5f);
                cs.setNonStrokingColor(new Color(15, 23, 42)); // Slate 900 text
                cs.newLineAtOffset(x + 12f, y - 2f);
                cs.showText(cLine);
                cs.endText();
                y -= 11.5f;
            }
            y -= 6f;
        }

        y -= 2f;

        // Multiple choice options: (A), (B), (C), (D)
        char optPrefix = 'A';
        for (List<String> optLineList : optLines) {
            boolean firstLine = true;
            for (String line : optLineList) {
                cs.beginText();
                if (firstLine) {
                    cs.setFont(fontBold, 9.5f);
                    cs.setNonStrokingColor(TEXT_MUTED);
                    cs.newLineAtOffset(x + 10f, y);
                    cs.showText(String.format("(%c) ", optPrefix));
                    cs.setFont(fontRegular, 9.5f);
                    cs.setNonStrokingColor(TEXT_DARK);
                    cs.showText(line);
                    firstLine = false;
                } else {
                    cs.setFont(fontRegular, 9.5f);
                    cs.setNonStrokingColor(TEXT_DARK);
                    cs.newLineAtOffset(x + 28f, y);
                    cs.showText(line);
                }
                cs.endText();
                y -= 12.5f;
            }
            optPrefix++;
            y -= 2f;
        }

        y -= 4f;

        // Correct Answer banner (Light green highlight)
        float correctBoxHeight = (correctLines.size() * 12.5f) + 8f;
        cs.setNonStrokingColor(SUCCESS_BG);
        cs.addRect(x + 6f, y - correctBoxHeight + 8f, width - 12f, correctBoxHeight);
        cs.fill();

        cs.setStrokingColor(new Color(187, 247, 208)); // Green 200 border
        cs.setLineWidth(0.5f);
        cs.addRect(x + 6f, y - correctBoxHeight + 8f, width - 12f, correctBoxHeight);
        cs.stroke();

        for (String cLine : correctLines) {
            cs.beginText();
            cs.setFont(fontBold, 9.5f);
            cs.setNonStrokingColor(SUCCESS_GREEN);
            cs.newLineAtOffset(x + 14f, y - 2f);
            cs.showText("[CORRECT] " + cLine);
            cs.endText();
            y -= 12.5f;
        }

        y -= 6f;

        // Explanation section if present
        if (!expLines.isEmpty() && !expLines.get(0).equals("Explanation: ")) {
            float expBoxHeight = (expLines.size() * 12f) + 8f;
            cs.setNonStrokingColor(EXPLANATION_BG);
            cs.addRect(x + 6f, y - expBoxHeight + 8f, width - 12f, expBoxHeight);
            cs.fill();

            cs.setStrokingColor(BORDER_COLOR);
            cs.setLineWidth(0.5f);
            cs.addRect(x + 6f, y - expBoxHeight + 8f, width - 12f, expBoxHeight);
            cs.stroke();

            for (String eLine : expLines) {
                cs.beginText();
                cs.setFont(fontRegular, 9f);
                cs.setNonStrokingColor(new Color(51, 65, 85));
                cs.newLineAtOffset(x + 14f, y - 2f);
                cs.showText(eLine);
                cs.endText();
                y -= 12f;
            }
            y -= 6f;
        }

        // Question divider line
        cs.setStrokingColor(BORDER_COLOR);
        cs.setLineWidth(0.5f);
        cs.moveTo(x, y);
        cs.lineTo(x + width, y);
        cs.stroke();

        return y;
    }

    private void drawFooter(PDPageContentStream cs, int pageNum, int totalPages,
            float x, float y, float width,
            PDFont fontRegular, PDFont fontOblique) throws Exception {
        // Divider line above footer
        cs.setStrokingColor(BORDER_COLOR);
        cs.setLineWidth(0.5f);
        cs.moveTo(x, y + 10f);
        cs.lineTo(x + width, y + 10f);
        cs.stroke();

        // Footer left: Brand & document info
        cs.beginText();
        cs.setFont(fontOblique, 8f);
        cs.setNonStrokingColor(TEXT_MUTED);
        cs.newLineAtOffset(x, y);
        cs.showText("DocMind RAG - Continuous Auto-Generated Quiz Document");
        cs.endText();

        // Footer right: Page number
        String pageStr = String.format("Page %d of %d", pageNum, totalPages);
        float pageStrWidth = fontRegular.getStringWidth(pageStr) / 1000f * 8f;
        cs.beginText();
        cs.setFont(fontRegular, 8f);
        cs.setNonStrokingColor(TEXT_MUTED);
        cs.newLineAtOffset(x + width - pageStrWidth, y);
        cs.showText(pageStr);
        cs.endText();
    }

    private List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }

        String clean = sanitize(text);
        String[] words = clean.split("\\s+");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            if (word.isBlank())
                continue;
            String test = current.length() == 0 ? word : current + " " + word;
            try {
                float width = font.getStringWidth(test) / 1000f * fontSize;
                if (width <= maxWidth) {
                    current = new StringBuilder(test);
                } else {
                    if (current.length() > 0) {
                        lines.add(current.toString());
                    }
                    current = new StringBuilder(word);
                }
            } catch (Exception e) {
                if (current.length() > 0) {
                    lines.add(current.toString());
                }
                current = new StringBuilder(word);
            }
        }

        if (current.length() > 0) {
            lines.add(current.toString());
        }

        return lines;
    }

    private List<String> wrapCodeSnippet(String code, PDFont font, float fontSize, float maxWidth) {
        List<String> result = new ArrayList<>();
        if (code == null || code.isBlank())
            return result;

        String clean = code
                .replace("\u201C", "\"")
                .replace("\u201D", "\"")
                .replace("\u2018", "'")
                .replace("\u2019", "'")
                .replace("\t", "    ")
                .replaceAll("[^\\x20-\\x7E\r\n]", " ");

        String[] rawLines = clean.split("\r?\n");
        for (String raw : rawLines) {
            String trimmedLine = raw.replaceAll("\\s+$", "");
            if (trimmedLine.isBlank()) {
                result.add("");
                continue;
            }
            try {
                float w = font.getStringWidth(trimmedLine) / 1000f * fontSize;
                if (w <= maxWidth) {
                    result.add(trimmedLine);
                } else {
                    StringBuilder sub = new StringBuilder();
                    for (char ch : trimmedLine.toCharArray()) {
                        String test = sub.toString() + ch;
                        if (font.getStringWidth(test) / 1000f * fontSize > maxWidth) {
                            result.add(sub.toString());
                            sub = new StringBuilder();
                        }
                        sub.append(ch);
                    }
                    if (sub.length() > 0)
                        result.add(sub.toString());
                }
            } catch (Exception e) {
                result.add(trimmedLine);
            }
        }
        return result;
    }

    private String sanitize(String input) {
        if (input == null)
            return "";

        return input
                .replace("\u201C", "\"") // left double quote
                .replace("\u201D", "\"") // right double quote
                .replace("\u2018", "'") // left single quote
                .replace("\u2019", "'") // right single quote
                .replace("\u2014", "--") // em dash
                .replace("\u2013", "-") // en dash
                .replace("\u2022", "*") // bullet
                .replace("\u25CF", "*") // black circle
                .replace("\u2713", "[v]")// checkmark
                .replace("\u2714", "[v]")// heavy checkmark
                .replace("\u2192", "->") // right arrow
                .replace("\u2026", "...")// ellipsis
                .replace("\u00A0", " ") // non-breaking space
                .replaceAll("[^\\x20-\\x7E]", " ") // replace any non-ASCII printable chars with space
                .replaceAll("\\s+", " ")
                .trim();
    }
}
