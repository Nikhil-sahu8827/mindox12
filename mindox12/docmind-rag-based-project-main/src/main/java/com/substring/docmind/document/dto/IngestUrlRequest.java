package com.substring.docmind.document.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestUrlRequest {

    @NotBlank(message = "URL cannot be empty")
    @URL(message = "Must be a valid HTTP or HTTPS URL")
    private String url;
}
