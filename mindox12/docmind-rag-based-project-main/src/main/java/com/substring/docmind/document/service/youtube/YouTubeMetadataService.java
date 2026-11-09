package com.substring.docmind.document.service.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class YouTubeMetadataService {

    private final YouTubeUrlService urlService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    @Builder
    public static class VideoMetadata {
        private String videoId;
        private String title;
        private String author;
        private String channelId;
        private String description;
        private long durationSeconds;
        private String formattedDuration;
        private long viewCount;
        private String thumbnailUrl;
        private List<String> keywords;
    }

    public VideoMetadata fetchMetadata(String videoId) {
        String canonicalUrl = urlService.buildCanonicalUrl(videoId);
        String title = "YouTube Video: " + videoId;
        String author = "YouTube Creator";
        String channelId = "";
        String description = "";
        long durationSeconds = 0;
        long viewCount = 0;
        String thumbnailUrl = urlService.buildThumbnailUrl(videoId);
        List<String> keywords = new ArrayList<>();

        // 1. Fetch via oEmbed
        try {
            String oembedUrl = "https://www.youtube.com/oembed?url=" + URLEncoder.encode(canonicalUrl, StandardCharsets.UTF_8) + "&format=json";
            org.jsoup.nodes.Document oembedDoc = Jsoup.connect(oembedUrl)
                    .ignoreContentType(true)
                    .timeout(8000)
                    .get();
            String oembedJson = oembedDoc.text();
            if (oembedJson != null && !oembedJson.isBlank()) {
                JsonNode node = objectMapper.readTree(oembedJson);
                if (node.has("title")) title = node.get("title").asText();
                if (node.has("author_name")) author = node.get("author_name").asText();
                if (node.has("thumbnail_url")) thumbnailUrl = node.get("thumbnail_url").asText();
            }
        } catch (Exception e) {
            log.debug("oEmbed fetch notice for {}: {}", videoId, e.getMessage());
        }

        // 2. Fetch watch page HTML
        try {
            org.jsoup.nodes.Document watchDoc = Jsoup.connect(canonicalUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(12000)
                    .get();

            String html = watchDoc.html();

            if (title.startsWith("YouTube Video:") && watchDoc.title() != null && !watchDoc.title().isBlank()) {
                title = watchDoc.title().replace(" - YouTube", "").trim();
            }

            org.jsoup.select.Elements metaDesc = watchDoc.select("meta[name=description]");
            if (!metaDesc.isEmpty()) {
                description = metaDesc.attr("content");
            }

            Pattern detailsPattern = Pattern.compile("\"videoDetails\":\\s*(\\{.+?\"isCrawlable\":[^}]+})");
            Matcher dm = detailsPattern.matcher(html);
            if (dm.find()) {
                try {
                    JsonNode vd = objectMapper.readTree(dm.group(1));
                    if (vd.has("title")) title = vd.get("title").asText();
                    if (vd.has("author")) author = vd.get("author").asText();
                    if (vd.has("channelId")) channelId = vd.get("channelId").asText();
                    if (vd.has("lengthSeconds")) durationSeconds = Long.parseLong(vd.get("lengthSeconds").asText());
                    if (vd.has("viewCount")) viewCount = Long.parseLong(vd.get("viewCount").asText());
                    if (vd.has("shortDescription") && (description.isBlank() || vd.get("shortDescription").asText().length() > description.length())) {
                        description = vd.get("shortDescription").asText();
                    }
                    if (vd.has("keywords")) {
                        for (JsonNode kw : vd.get("keywords")) {
                            keywords.add(kw.asText());
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.debug("Watch page parse notice for {}: {}", videoId, e.getMessage());
        }

        String formattedDuration = formatDuration(durationSeconds);

        return VideoMetadata.builder()
                .videoId(videoId)
                .title(title)
                .author(author)
                .channelId(channelId)
                .description(description)
                .durationSeconds(durationSeconds)
                .formattedDuration(formattedDuration)
                .viewCount(viewCount)
                .thumbnailUrl(thumbnailUrl)
                .keywords(keywords)
                .build();
    }

    public static String formatDuration(long totalSeconds) {
        if (totalSeconds <= 0) return "00:00";
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }
}
