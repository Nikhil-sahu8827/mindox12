package com.substring.docmind.user.service;

import com.substring.docmind.security.UserPrincipal;
import com.substring.docmind.user.dto.UserDto;
import com.substring.docmind.user.entity.User;

import java.util.List;

public interface UserService {

    UserDto getCurrentUserProfile(Long userId);

    User getUserById(Long userId);

    List<UserDto> getAllUsers();

    void deleteUser(Long userId, UserPrincipal currentUser);
}
