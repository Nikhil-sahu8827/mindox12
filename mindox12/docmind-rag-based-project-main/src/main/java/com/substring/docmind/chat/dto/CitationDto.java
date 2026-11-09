package com.substring.docmind.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitationDto {

    private String documentId;
    private String fileName;
    private String text;
    private Integer pageNumber;
    private Double score;
}
