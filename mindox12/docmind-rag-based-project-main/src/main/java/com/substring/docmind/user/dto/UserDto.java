package com.substring.docmind.user.dto;

import com.substring.docmind.enums.AuthProvider;
import com.substring.docmind.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private Long id;
    private String username;
    private String email;
    private Role role;
    private AuthProvider provider;
    private String imageUrl;
    private LocalDateTime createdAt;
}
