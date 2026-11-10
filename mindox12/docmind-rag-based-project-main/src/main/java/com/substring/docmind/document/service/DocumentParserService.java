package com.substring.docmind.document.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
public class DocumentParserService {

    private static final int CHUNK_SIZE = 800; // characters per chunk
    private static final int CHUNK_OVERLAP = 150;

    public int extractPageCount(MultipartFile file) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        try {
            return extractPageCount(file.getBytes(), filename);
        } catch (Exception e) {
            log.warn("Could not determine page count for file {}: {}", filename, e.getMessage());
            return 1;
        }
    }

    public int extractPageCount(byte[] bytes, String filename) {
        String fn = filename != null ? filename.toLowerCase() : "";
        if (fn.endsWith(".pdf")) {
            try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(bytes)) {
                return Math.max(1, document.getNumberOfPages());
            } catch (Exception e) {
                log.warn("Could not read page count for PDF {}: {}", filename, e.getMessage());
            }
        }
        return 1;
    }

    public String extractText(MultipartFile file) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        try {
            return extractText(file.getBytes(), filename);
        } catch (Exception e) {
            log.error("Failed to extract text from MultipartFile {}: {}", filename, e.getMessage(), e);
            throw new RuntimeException("Could not parse file content: " + e.getMessage(), e);
        }
    }

    public String extractText(byte[] bytes, String filename) {
        String fn = filename != null ? filename.toLowerCase() : "";
        log.info("Extracting text from file: {} ({} bytes)", filename, bytes.length);

        try {
            if (fn.endsWith(".pdf")) {
                return extractPdfText(bytes);
            } else if (fn.endsWith(".docx")) {
                return extractDocxText(bytes);
            } else if (fn.endsWith(".csv")) {
                return extractCsvText(bytes);
            } else if (fn.endsWith(".html") || fn.endsWith(".htm")) {
                String html = new String(bytes, StandardCharsets.UTF_8);
                return Jsoup.parse(html).text().replace("\u0000", "").trim();
            } else {
                // Default for txt, md, json, xml, log, yml, etc.
                return new String(bytes, StandardCharsets.UTF_8).replace("\u0000", "").trim();
            }
        } catch (Exception e) {
            log.error("Failed to extract text from file: {}", filename, e);
            throw new RuntimeException("Could not parse file content: " + e.getMessage(), e);
        }
    }

    private String extractPdfText(byte[] bytes) {
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(bytes)) {
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            if (text != null && !text.isBlank()) {
                return text.replace("\u0000", "").trim();
            }
        } catch (Exception e) {
            log.warn("Standard PDF parser encountered an issue ({}), trying raw text extraction", e.getMessage());
        }

        // Resilient Fallback: extract textual tokens from PDF stream directly
        try {
            String raw = new String(bytes, StandardCharsets.ISO_8859_1);
            StringBuilder extracted = new StringBuilder();
            int idx = 0;
            while ((idx = raw.indexOf("BT", idx)) != -1) {
                int end = raw.indexOf("ET", idx);
                if (end != -1 && end - idx < 5000) {
                    String block = raw.substring(idx, end);
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\(([^)]+)\\)").matcher(block);
                    while (m.find()) {
                        extracted.append(m.group(1)).append(" ");
                    }
                    extracted.append("\n");
                    idx = end + 2;
                } else {
                    idx += 2;
                }
            }
            String result = extracted.toString().trim();
            if (!result.isBlank()) {
                return result;
            }
        } catch (Exception ignored) {}

        log.warn("Could not extract textual content from PDF, using fallback document reference");
        return "PDF document content could not be converted to plain text.";
    }

    private String extractDocxText(byte[] bytes) {
        log.info("Extracting text from DOCX document");
        StringBuilder sb = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    byte[] xmlBytes = zis.readAllBytes();
                    String xml = new String(xmlBytes, StandardCharsets.UTF_8);
                    org.jsoup.nodes.Document doc = Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser());
                    for (org.jsoup.nodes.Element p : doc.select("w|p")) {
                        String pText = p.select("w|t").stream()
                                .map(org.jsoup.nodes.Element::text)
                                .collect(Collectors.joining(""));
                        if (!pText.isBlank()) {
                            sb.append(pText.trim()).append("\n\n");
                        }
                    }
                    break;
                }
            }
            String result = sb.toString().replace("\u0000", "").trim();
            if (result.isBlank()) {
                log.warn("DOCX text extraction resulted in empty string, checking fallback");
            }
            return result;
        } catch (Exception e) {
            log.error("Error parsing DOCX file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to extract DOCX text: " + e.getMessage(), e);
        }
    }

    private String extractCsvText(byte[] bytes) {
        log.info("Extracting and structuring CSV document");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            List<String> lines = reader.lines().toList();
            if (lines.isEmpty()) {
                return "";
            }

            String headerLine = lines.get(0);
            List<String> headers = parseCsvLine(headerLine);
            StringBuilder sb = new StringBuilder();
            sb.append("Table Data [Columns: ").append(String.join(", ", headers)).append("]:\n\n");

            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isBlank()) continue;
                List<String> values = parseCsvLine(line);
                sb.append(String.format("[Row %d] ", i));
                for (int h = 0; h < headers.size(); h++) {
                    String val = h < values.size() ? values.get(h) : "";
                    sb.append(headers.get(h)).append(": ").append(val);
                    if (h < headers.size() - 1) sb.append(" | ");
                }
                sb.append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.error("Error parsing CSV: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse CSV file: " + e.getMessage(), e);
        }
    }

    private List<String> parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens;
    }

    public String extractTextFromUrl(String url) {
        log.info("Scraping webpage text from URL: {}", url);
        try {
            org.jsoup.nodes.Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) DocMindBot/1.0")
                    .timeout(15000)
                    .get();

            String title = doc.title();
            String bodyText = doc.body().text();
            return title + "\n\n" + bodyText;
        } catch (Exception e) {
            log.error("Failed to scrape text from URL: {}", url, e);
            throw new RuntimeException("Could not retrieve web page content: " + e.getMessage(), e);
        }
    }

    public List<Document> chunkText(String fullText, String documentId, Long userId, String fileName, String contentType) {
        return chunkText(fullText, documentId, userId, fileName, contentType, 1, false);
    }

    public List<Document> chunkText(String fullText, String documentId, Long userId, String fileName, String contentType, int totalPages, boolean isGlobal) {
        log.info("Chunking text (length: {}) for document: {}, user: {}, isGlobal: {}", fullText.length(), documentId, userId, isGlobal);

        List<Document> chunks = new ArrayList<>();
        if (fullText == null || fullText.isBlank()) {
            return chunks;
        }

        String cleanText = fullText.replace("\u0000", "").replaceAll("\\r\\n", "\n").replaceAll("\\s+", " ").trim();
        int length = cleanText.length();
        int start = 0;
        int chunkIndex = 0;

        while (start < length) {
            int end = Math.min(start + CHUNK_SIZE, length);

            // Avoid breaking words
            if (end < length) {
                int lastSpace = cleanText.lastIndexOf(' ', end);
                if (lastSpace > start + 200) {
                    end = lastSpace;
                }
            }

            String chunkContent = cleanText.substring(start, end).replace("\u0000", "").trim();
            if (!chunkContent.isEmpty()) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("documentId", documentId);
                metadata.put("userId", userId);
                metadata.put("fileName", fileName != null ? fileName.replace("\u0000", "") : "");
                metadata.put("contentType", contentType);
                metadata.put("chunkIndex", chunkIndex++);
                
                // Approximate page number if multiple pages
                int pageNum = 1;
                if (totalPages > 1) {
                    pageNum = Math.min(totalPages, (int) Math.floor((double) start / length * totalPages) + 1);
                }
                metadata.put("pageNumber", pageNum);
                metadata.put("isGlobal", isGlobal);

                chunks.add(new Document(chunkContent, metadata));
            }

            if (end >= length) {
                break;
            }
            start = Math.max(end - CHUNK_OVERLAP, start + 1);
        }

        log.info("Created {} chunks for document: {}", chunks.size(), documentId);
        return chunks;
    }
}

