package com.substring.docmind.auth.dto;

import com.substring.docmind.user.dto.UserDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private UserDto user;
}
