package com.substring.docmind.document.dto.youtube;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptSegmentDto {
    private long startMs;
    private long durationMs;
    private String timestamp; // formatted e.g. "01:23" or "01:14:05"
    private String text;
    private String lang;
}
