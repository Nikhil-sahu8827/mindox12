package com.substring.docmind.auth.controller;

import com.substring.docmind.auth.dto.LoginRequest;
import com.substring.docmind.auth.dto.LoginResponse;
import com.substring.docmind.auth.dto.RegisterRequest;
import com.substring.docmind.auth.dto.SocialLoginRequest;
import com.substring.docmind.auth.service.AuthService;
import com.substring.docmind.user.dto.UserDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user registration, local login, and OAuth social login")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register new user", description = "Creates a new user profile with local credentials")
    public ResponseEntity<UserDto> register(@Valid @RequestBody RegisterRequest request) {
        log.info("REST request to register user: {}", request.getUsername());
        UserDto createdUser = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user", description = "Authenticates user credentials and issues a JWT token")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("REST request to login user: {}", request.getUsername());
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/social-login")
    @Operation(summary = "OAuth Social Login", description = "Authenticates or registers user via third-party OAuth provider")
    public ResponseEntity<LoginResponse> socialLogin(@Valid @RequestBody SocialLoginRequest request) {
        log.info("REST request to social login for email: {}", request.getEmail());
        LoginResponse response = authService.socialLogin(request);
        return ResponseEntity.ok(response);
    }
}
