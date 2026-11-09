package com.substring.docmind.document.dto.youtube;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class YouTubeChatDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @NotBlank(message = "Question cannot be empty")
        private String question;
        private String videoId;
        private String documentId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String answer;
        private List<String> timestamps; // e.g. ["14:20", "28:45"]
        private boolean foundInVideo;
        private List<TranscriptSegmentDto> relevantSegments;
    }
}
