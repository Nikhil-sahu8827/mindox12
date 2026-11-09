package com.substring.docmind.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestYouTubeRequest {

    private String url;

    private String youtubeUrl;

    public String getEffectiveUrl() {
        if (url != null && !url.isBlank()) {
            return url.trim();
        }
        if (youtubeUrl != null && !youtubeUrl.isBlank()) {
            return youtubeUrl.trim();
        }
        return null;
    }
}
