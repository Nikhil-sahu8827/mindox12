package com.substring.docmind.document.service.youtube;

import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class YouTubeUrlService {

    private static final Pattern YOUTUBE_ID_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.|m\\.)?(?:youtube\\.com/(?:watch\\?v=|embed/|v/|shorts/|.+[?&]v=)|youtu\\.be/)([a-zA-Z0-9_-]{11})"
    );

    public boolean isYouTubeUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String trimmed = url.trim();
        return trimmed.contains("youtube.com") || trimmed.contains("youtu.be") || YOUTUBE_ID_PATTERN.matcher(trimmed).find();
    }

    public String extractVideoId(String url) {
        if (url == null || url.isBlank()) return null;
        String trimmed = url.trim();
        Matcher matcher = YOUTUBE_ID_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1);
        }
        if (trimmed.length() == 11 && trimmed.matches("^[a-zA-Z0-9_-]{11}$")) {
            return trimmed;
        }
        return null;
    }

    public String buildCanonicalUrl(String videoId) {
        if (videoId == null || videoId.isBlank()) return null;
        return "https://www.youtube.com/watch?v=" + videoId.trim();
    }

    public String buildEmbedUrl(String videoId) {
        if (videoId == null || videoId.isBlank()) return null;
        return "https://www.youtube.com/embed/" + videoId.trim();
    }

    public String buildThumbnailUrl(String videoId) {
        if (videoId == null || videoId.isBlank()) return null;
        return "https://img.youtube.com/vi/" + videoId.trim() + "/hqdefault.jpg";
    }

    public String buildMaxResThumbnailUrl(String videoId) {
        if (videoId == null || videoId.isBlank()) return null;
        return "https://img.youtube.com/vi/" + videoId.trim() + "/maxresdefault.jpg";
    }
}
