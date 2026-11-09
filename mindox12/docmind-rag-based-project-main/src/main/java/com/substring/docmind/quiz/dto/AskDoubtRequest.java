package com.substring.docmind.quiz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AskDoubtRequest {

    private String doubtText;
    private String message;
    private String userSelectedAnswer;
    private List<Object> history;

    public String getEffectiveDoubt() {
        if (message != null && !message.isBlank()) {
            return message.trim();
        }
        if (doubtText != null && !doubtText.isBlank()) {
            return doubtText.trim();
        }
        return "Why is this the correct answer?";
    }
}
