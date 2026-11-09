package com.substring.docmind.document.dto.youtube;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessYouTubeRequest {
    @NotBlank(message = "YouTube URL is required")
    private String youtubeUrl;
    private String language; // optional preferred language code, e.g. "en", "hi"
}
