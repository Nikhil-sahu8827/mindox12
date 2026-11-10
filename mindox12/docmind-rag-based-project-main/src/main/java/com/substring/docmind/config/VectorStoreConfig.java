package com.substring.docmind.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Slf4j
@Configuration
public class VectorStoreConfig {

    @Bean
    @Primary
    public EmbeddingModel primaryEmbeddingModel(
            @Value("${spring.ai.openai.api-key:mock-key}") String apiKey,
            ObjectProvider<OpenAiEmbeddingModel> openAiEmbeddingModelProvider) {

        EmbeddingModel fallbackModel = createFallbackEmbeddingModel();

        boolean isPlaceholderKey = apiKey == null
                || apiKey.isBlank()
                || "mock-key".equalsIgnoreCase(apiKey)
                || "your-gemini-api-key".equalsIgnoreCase(apiKey)
                || apiKey.startsWith("your-")
                || apiKey.contains("dummy");

        if (!isPlaceholderKey) {
            OpenAiEmbeddingModel openAiModel = openAiEmbeddingModelProvider.getIfAvailable();
            if (openAiModel != null) {
                log.info("Using real OpenAiEmbeddingModel with configured API key.");
                return new EmbeddingModel() {
                    private volatile boolean openAiFailed = false;

                    @Override
                    public org.springframework.ai.embedding.EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request) {
                        if (openAiFailed) {
                            return fallbackModel.call(request);
                        }
                        try {
                            return openAiModel.call(request);
                        } catch (Exception e) {
                            openAiFailed = true;
                            log.warn("OpenAI embedding API call failed ({}), falling back to deterministic embedding.", e.getMessage());
                            return fallbackModel.call(request);
                        }
                    }

                    @Override
                    public float[] embed(org.springframework.ai.document.Document document) {
                        if (openAiFailed) {
                            return fallbackModel.embed(document);
                        }
                        try {
                            return openAiModel.embed(document);
                        } catch (Exception e) {
                            openAiFailed = true;
                            log.warn("OpenAI embed document failed ({}), falling back to deterministic embedding.", e.getMessage());
                            return fallbackModel.embed(document);
                        }
                    }

                    @Override
                    public float[] embed(String text) {
                        if (openAiFailed) {
                            return fallbackModel.embed(text);
                        }
                        try {
                            return openAiModel.embed(text);
                        } catch (Exception e) {
                            openAiFailed = true;
                            log.warn("OpenAI embed text failed ({}), falling back to deterministic embedding.", e.getMessage());
                            return fallbackModel.embed(text);
                        }
                    }
                };
            }
        }

        log.info("Using deterministic fallback embedding model (no active OpenAI key required).");
        return fallbackModel;
    }

    private EmbeddingModel createFallbackEmbeddingModel() {
        return new EmbeddingModel() {
            @Override
            public org.springframework.ai.embedding.EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request) {
                List<org.springframework.ai.embedding.Embedding> embeddings = request.getInstructions().stream()
                        .map(text -> new org.springframework.ai.embedding.Embedding(generateSemanticFeatureVector(text, 1536), 0))
                        .toList();
                return new org.springframework.ai.embedding.EmbeddingResponse(embeddings);
            }

            @Override
            public float[] embed(org.springframework.ai.document.Document document) {
                return generateSemanticFeatureVector(document.getText(), 1536);
            }

            @Override
            public float[] embed(String text) {
                return generateSemanticFeatureVector(text, 1536);
            }

            private float[] generateSemanticFeatureVector(String text, int dim) {
                float[] vec = new float[dim];
                if (text == null || text.isBlank()) {
                    return vec;
                }

                String normalized = text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ");
                String[] words = normalized.split("\\s+");

                for (String word : words) {
                    if (word.isBlank()) continue;
                    // Word unigram feature
                    int h1 = word.hashCode();
                    int idx1 = Math.abs(h1) % dim;
                    float sign1 = (h1 % 2 == 0) ? 1.0f : -1.0f;
                    vec[idx1] += sign1 * (word.length() > 3 ? 2.0f : 1.0f);

                    // Subword tri-grams for morphological/stemming overlap
                    if (word.length() >= 3) {
                        for (int i = 0; i <= word.length() - 3; i++) {
                            String tri = word.substring(i, i + 3);
                            int hTri = tri.hashCode();
                            int idxTri = Math.abs(hTri) % dim;
                            float signTri = (hTri % 2 == 0) ? 0.5f : -0.5f;
                            vec[idxTri] += signTri;
                        }
                    }
                }

                // Bigram features for multi-word phrases (e.g. "exception handling", "cloud computing", "generics in java")
                for (int i = 0; i < words.length - 1; i++) {
                    if (words[i].isBlank() || words[i + 1].isBlank()) continue;
                    String bigram = words[i] + "_" + words[i + 1];
                    int h2 = bigram.hashCode();
                    int idx2 = Math.abs(h2) % dim;
                    float sign2 = (h2 % 2 == 0) ? 1.5f : -1.5f;
                    vec[idx2] += sign2;
                }

                // L2-normalize to unit length so dot product is exact cosine similarity
                double normSq = 0.0;
                for (float v : vec) {
                    normSq += v * v;
                }
                if (normSq > 1e-9) {
                    float norm = (float) Math.sqrt(normSq);
                    for (int i = 0; i < dim; i++) {
                        vec[i] /= norm;
                    }
                }
                return vec;
            }
        };
    }

    @Bean
    @Primary
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        log.info("Configuring VectorStore with embedding model.");
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @jakarta.annotation.PostConstruct
    public void initVectorStoreTable() {
        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.execute(
                        "CREATE TABLE IF NOT EXISTS vector_store (" +
                        "  id VARCHAR(64) PRIMARY KEY, " +
                        "  content TEXT, " +
                        "  metadata JSONB" +
                        ")"
                );
                log.info("Successfully verified/initialized vector_store table in PostgreSQL");
            } catch (Exception e) {
                log.warn("Notice during vector_store auto-initialization: {}", e.getMessage());
            }
        }
    }
}
