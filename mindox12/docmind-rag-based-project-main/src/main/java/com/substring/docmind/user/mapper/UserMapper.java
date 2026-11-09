package com.substring.docmind.user.mapper;

import com.substring.docmind.user.dto.UserDto;
import com.substring.docmind.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserDto toDto(User user) {
        if (user == null) {
            return null;
        }

        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .provider(user.getProvider())
                .imageUrl(user.getImageUrl())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
