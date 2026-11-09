package com.substring.docmind.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Minimum combined relevance threshold to consider a chunk actually relevant to the query
    public static final double MIN_RELEVANCE_THRESHOLD = 0.40;

    public List<Document> searchRelevantChunks(String query, Long userId, boolean isAdmin, String documentId, int topK) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        String cleanedQuery = query.trim();
        log.info("Performing precision vector & keyword search for query: '{}', userId: {}, isAdmin: {}, documentId: {}, topK: {}",
                cleanedQuery, userId, isAdmin, documentId, topK);

        String targetDocId = (documentId != null && !documentId.isBlank()
                && !documentId.equalsIgnoreCase("all") && !documentId.equalsIgnoreCase("global"))
                ? documentId.trim() : null;

        // Dedicated Handling for Overview / Executive Summary queries (targeted or home/all)
        if (isOverviewOrSummaryQuery(cleanedQuery) && isVectorStoreTablePresent()) {
            try {
                String seqSql;
                if (targetDocId != null) {
                    seqSql = "SELECT content, metadata FROM vector_store " +
                            "WHERE (? = true OR metadata->>'userId' = ? OR metadata->>'user_id' = ? OR metadata->>'isGlobal' = 'true') " +
                            "AND (metadata->>'documentId' = ? OR metadata->>'document_id' = ?) " +
                            "ORDER BY CAST(COALESCE(metadata->>'chunkIndex', metadata->>'chunk_index', '0') AS integer) ASC LIMIT ?;";
                } else {
                    seqSql = "SELECT content, metadata FROM vector_store " +
                            "WHERE (? = true OR metadata->>'userId' = ? OR metadata->>'user_id' = ? OR metadata->>'isGlobal' = 'true') " +
                            "AND CAST(COALESCE(metadata->>'chunkIndex', metadata->>'chunk_index', '0') AS integer) <= 2 " +
                            "ORDER BY ctid DESC LIMIT ?;";
                }
                List<Document> seqDocs = new ArrayList<>();
                jdbcTemplate.query(
                        seqSql,
                        ps -> {
                            ps.setBoolean(1, isAdmin);
                            ps.setString(2, String.valueOf(userId));
                            ps.setString(3, String.valueOf(userId));
                            if (targetDocId != null) {
                                ps.setString(4, targetDocId);
                                ps.setString(5, targetDocId);
                                ps.setInt(6, Math.max(topK, 8));
                            } else {
                                ps.setInt(4, Math.max(topK, 8));
                            }
                        },
                        (rs, rowNum) -> {
                            String content = rs.getString("content");
                            String metaJson = rs.getString("metadata");
                            Map<String, Object> metadata = parseMetadata(metaJson);
                            seqDocs.add(Document.builder()
                                    .text(content)
                                    .metadata(metadata)
                                    .score(1.0)
                                    .build());
                            return null;
                        }
                );
                if (!seqDocs.isEmpty()) {
                    log.info("Returning {} sequential chunks for overview/summary of document {}", seqDocs.size(), targetDocId);
                    return seqDocs;
                }
            } catch (Exception e) {
                log.warn("Failed fetching sequential chunks for document {}: {}", targetDocId, e.getMessage());
            }
        }

        List<String> keywords = extractKeywords(cleanedQuery);
        Map<String, ScoredCandidate> candidatesMap = new HashMap<>();

        // 1. Semantic PgVector Search
        if (isVectorStoreTablePresent()) {
            try {
                float[] queryEmbedding = embeddingModel.embed(cleanedQuery);
                String vectorString = formatVector(queryEmbedding);

                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append("SELECT content, metadata, (1.0 - (embedding <=> CAST(? AS vector))) AS similarity_score ");
                sqlBuilder.append("FROM vector_store ");
                sqlBuilder.append("WHERE (? = true OR metadata->>'userId' = ? OR metadata->>'user_id' = ? OR metadata->>'isGlobal' = 'true') ");
                if (targetDocId != null) {
                    sqlBuilder.append("AND (metadata->>'documentId' = ? OR metadata->>'document_id' = ?) ");
                }
                sqlBuilder.append("ORDER BY embedding <=> CAST(? AS vector) LIMIT 20;");

                jdbcTemplate.query(
                        sqlBuilder.toString(),
                        ps -> {
                            int idx = 1;
                            ps.setString(idx++, vectorString);
                            ps.setBoolean(idx++, isAdmin);
                            ps.setString(idx++, String.valueOf(userId));
                            ps.setString(idx++, String.valueOf(userId));
                            if (targetDocId != null) {
                                ps.setString(idx++, targetDocId);
                                ps.setString(idx++, targetDocId);
                            }
                            ps.setString(idx++, vectorString);
                        },
                        (rs, rowNum) -> {
                            String content = rs.getString("content");
                            String metaJson = rs.getString("metadata");
                            double vecScore = Math.max(0.0, Math.min(1.0, rs.getDouble("similarity_score")));
                            Map<String, Object> metadata = parseMetadata(metaJson);

                            String key = content.trim();
                            candidatesMap.compute(key, (k, existing) -> {
                                if (existing == null) {
                                    return new ScoredCandidate(content, metadata, vecScore, 0.0);
                                } else {
                                    existing.vectorScore = Math.max(existing.vectorScore, vecScore);
                                    return existing;
                                }
                            });
                            return null;
                        }
                );
                log.info("PgVector retrieved {} candidate chunks", candidatesMap.size());
            } catch (Exception e) {
                log.warn("PgVector search failed ({}), continuing with keyword search", e.getMessage());
            }
        }

        // 2. Keyword & Entity-based Search
        if (!keywords.isEmpty() && isVectorStoreTablePresent()) {
            try {
                StringBuilder kwSql = new StringBuilder();
                kwSql.append("SELECT content, metadata ");
                kwSql.append("FROM vector_store ");
                kwSql.append("WHERE (? = true OR metadata->>'userId' = ? OR metadata->>'user_id' = ? OR metadata->>'isGlobal' = 'true') ");
                if (targetDocId != null) {
                    kwSql.append("AND (metadata->>'documentId' = ? OR metadata->>'document_id' = ?) ");
                }
                kwSql.append("AND (");
                for (int i = 0; i < keywords.size(); i++) {
                    if (i > 0) kwSql.append(" OR ");
                    kwSql.append("content ILIKE ? OR metadata->>'fileName' ILIKE ?");
                }
                kwSql.append(") LIMIT 20;");

                jdbcTemplate.query(
                        kwSql.toString(),
                        ps -> {
                            int idx = 1;
                            ps.setBoolean(idx++, isAdmin);
                            ps.setString(idx++, String.valueOf(userId));
                            ps.setString(idx++, String.valueOf(userId));
                            if (targetDocId != null) {
                                ps.setString(idx++, targetDocId);
                                ps.setString(idx++, targetDocId);
                            }
                            for (String kw : keywords) {
                                String pat = "%" + kw + "%";
                                ps.setString(idx++, pat);
                                ps.setString(idx++, pat);
                            }
                        },
                        (rs, rowNum) -> {
                            String content = rs.getString("content");
                            String metaJson = rs.getString("metadata");
                            Map<String, Object> metadata = parseMetadata(metaJson);

                            String key = content.trim();
                            candidatesMap.computeIfAbsent(key, k -> new ScoredCandidate(content, metadata, 0.0, 0.0));
                            return null;
                        }
                );
            } catch (Exception e) {
                log.warn("Keyword search failed: {}", e.getMessage());
            }
        }

        // If no candidates found from native queries, try VectorStore fallback
        if (candidatesMap.isEmpty()) {
            List<Document> fbDocs = fallbackSearch(cleanedQuery, userId, isAdmin, targetDocId, topK * 2);
            for (Document doc : fbDocs) {
                candidatesMap.put(doc.getText().trim(),
                        new ScoredCandidate(doc.getText(), doc.getMetadata(),
                                doc.getScore() != null ? doc.getScore() : 0.5, 0.0));
            }
        }

        // 3. Compute Composite Relevance Score & Apply Strict Filtering
        List<Document> scoredAndFiltered = new ArrayList<>();
        String lowerQuery = cleanedQuery.toLowerCase();

        for (ScoredCandidate cand : candidatesMap.values()) {
            String content = cand.content != null ? cand.content : "";
            String lowerContent = content.toLowerCase();
            String fileName = cand.metadata != null ? String.valueOf(cand.metadata.getOrDefault("fileName", "")) : "";
            String lowerFileName = fileName.toLowerCase();

            // Calculate lexical & keyword relevance
            double keywordScore = 0.0;
            int matchedKeywords = 0;
            for (String kw : keywords) {
                String lKw = kw.toLowerCase();
                boolean inContent = lowerContent.contains(lKw);
                boolean inFileName = lowerFileName.contains(lKw);
                if (inContent || inFileName) {
                    matchedKeywords++;
                    double weight = (lKw.length() >= 6) ? 0.35 : 0.20;
                    keywordScore += inFileName ? (weight + 0.15) : weight;
                }
            }

            // Exact phrase match bonus
            if (!cleanedQuery.isBlank() && cleanedQuery.length() > 4) {
                if (lowerContent.contains(lowerQuery) || lowerFileName.contains(lowerQuery)) {
                    keywordScore += 0.40;
                }
            }

            // Keyword coverage ratio
            double keywordCoverage = keywords.isEmpty() ? 0.0 : ((double) matchedKeywords / keywords.size());
            keywordScore = Math.min(1.0, keywordScore + (keywordCoverage * 0.30));

            // Combined Hybrid Score
            double hybridScore;
            if (cand.vectorScore > 0 && keywordScore > 0) {
                hybridScore = (cand.vectorScore * 0.45) + (keywordScore * 0.55);
            } else if (cand.vectorScore > 0) {
                hybridScore = cand.vectorScore * 0.85;
            } else {
                hybridScore = keywordScore * 0.90;
            }

            // Strict Relevance Gate:
            // Eliminates irrelevant documents while capturing genuine multi-topic matches
            boolean phraseMatch = lowerContent.contains(lowerQuery) || lowerFileName.contains(lowerQuery);
            boolean strongKeywordMatch = keywordCoverage >= 0.40 && keywordScore >= 0.35;
            boolean solidHybrid = hybridScore >= MIN_RELEVANCE_THRESHOLD && (keywordCoverage >= 0.20 || cand.vectorScore >= 0.45 || keywords.size() <= 2);
            boolean targetDocMatch = (targetDocId != null && (cand.vectorScore >= 0.30 || hybridScore >= 0.30));
            boolean overviewMatch = isOverviewOrSummaryQuery(cleanedQuery);

            if (phraseMatch || strongKeywordMatch || solidHybrid || targetDocMatch || overviewMatch) {
                cand.finalScore = Math.max(hybridScore, Math.max(cand.vectorScore, keywordScore));
                Document doc = Document.builder()
                        .text(cand.content)
                        .metadata(cand.metadata)
                        .score(cand.finalScore)
                        .build();
                scoredAndFiltered.add(doc);
            } else {
                log.debug("Discarding irrelevant chunk from '{}' (score={}): {}", fileName, hybridScore,
                        content.length() > 60 ? content.substring(0, 60) + "..." : content);
            }
        }

        // Safety Fallback: If strict gate discarded all candidates, but targetDocId or candidates exist
        if (scoredAndFiltered.isEmpty() && !candidatesMap.isEmpty()) {
            if (targetDocId != null || isOverviewOrSummaryQuery(cleanedQuery)) {
                log.info("Strict gate yielded 0 results; using top candidate chunks from knowledge base");
                for (ScoredCandidate cand : candidatesMap.values()) {
                    scoredAndFiltered.add(Document.builder()
                            .text(cand.content)
                            .metadata(cand.metadata)
                            .score(Math.max(0.40, cand.vectorScore))
                            .build());
                }
            }
        }

        // Sort descending by relevance score
        scoredAndFiltered.sort((a, b) -> Double.compare(
                b.getScore() != null ? b.getScore() : 0.0,
                a.getScore() != null ? a.getScore() : 0.0
        ));

        List<Document> result = scoredAndFiltered.stream().limit(topK).collect(Collectors.toList());
        log.info("Returning {} strictly relevant chunks out of {} evaluated candidates",
                result.size(), candidatesMap.size());
        return result;
    }

    private List<Document> fallbackSearch(String query, Long userId, boolean isAdmin, String targetDocId, int topK) {
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(query)
                    .topK(topK * 4);

            List<Document> results = vectorStore.similaritySearch(builder.build());

            return results.stream()
                    .filter(doc -> {
                        Object chunkUserId = doc.getMetadata().get("userId");
                        Object isGlobal = doc.getMetadata().get("isGlobal");
                        boolean globalMatch = Boolean.TRUE.equals(isGlobal) || "true".equalsIgnoreCase(String.valueOf(isGlobal));

                        boolean userMatch = isAdmin || globalMatch ||
                                (chunkUserId != null && String.valueOf(chunkUserId).equals(String.valueOf(userId)));

                        if (!userMatch) return false;

                        if (targetDocId != null) {
                            Object chunkDocId = doc.getMetadata().get("documentId") != null
                                    ? doc.getMetadata().get("documentId")
                                    : doc.getMetadata().get("document_id");
                            return targetDocId.equals(chunkDocId);
                        }
                        return true;
                    })
                    .limit(topK)
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            log.error("Fallback vector search failed: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private String formatVector(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Could not parse chunk metadata JSON: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    public boolean isOverviewOrSummaryQuery(String query) {
        if (query == null || query.isBlank()) return false;
        String l = query.toLowerCase();
        return l.contains("summary") || l.contains("summarize") || l.contains("summarise")
                || l.contains("overview") || l.contains("executive summary")
                || l.contains("key point") || l.contains("key points")
                || l.contains("main point") || l.contains("main points")
                || l.contains("main concept") || l.contains("main concepts")
                || l.contains("outline") || l.contains("takeaway") || l.contains("takeaways")
                || l.contains("brief") || l.contains("explain this")
                || l.contains("what is this") || l.contains("about this document")
                || l.contains("about this file") || l.contains("in this document")
                || l.contains("in this file") || l.contains("kya content")
                || l.contains("kya hai") || l.contains("summary batao")
                || l.contains("samjhao") || l.contains("batao");
    }

    public List<String> extractKeywords(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        // Remove common question words, prepositions, articles, auxiliary verbs, and meta/instruction words
        String cleaned = query.replaceAll("[^a-zA-Z0-9\\s]", " ")
                .replaceAll("(?i)\\b(what|is|are|the|how|why|when|where|who|whom|which|can|could|would|should|will|do|does|did|tell|me|about|explain|give|describe|in|on|at|of|a|an|the|to|for|with|from|by|and|or|as|that|this|these|those|there|here|it|its|you|your|my|our|provide|concise|executive|summary|summarize|summarise|key|points|point|main|overview|outline|brief|document|documents|file|files|pdf)\\b", " ")
                .trim();
        String[] words = cleaned.split("\\s+");
        List<String> list = new ArrayList<>();
        for (String w : words) {
            String term = w.trim();
            if (term.length() >= 3 && !list.contains(term)) {
                list.add(term);
            }
        }
        return list;
    }

    private static class ScoredCandidate {
        String content;
        Map<String, Object> metadata;
        double vectorScore;
        double finalScore;

        ScoredCandidate(String content, Map<String, Object> metadata, double vectorScore, double finalScore) {
            this.content = content;
            this.metadata = metadata;
            this.vectorScore = vectorScore;
            this.finalScore = finalScore;
        }
    }

    private boolean isVectorStoreTablePresent() {
        if (jdbcTemplate == null) return false;
        try {
            Boolean exists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'vector_store')",
                    Boolean.class
            );
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            return false;
        }
    }
}
