package com.substring.docmind.auth.service;

import com.substring.docmind.auth.dto.LoginRequest;
import com.substring.docmind.auth.dto.LoginResponse;
import com.substring.docmind.auth.dto.RegisterRequest;
import com.substring.docmind.auth.dto.SocialLoginRequest;
import com.substring.docmind.user.dto.UserDto;

public interface AuthService {

    UserDto register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    LoginResponse socialLogin(SocialLoginRequest request);
}
