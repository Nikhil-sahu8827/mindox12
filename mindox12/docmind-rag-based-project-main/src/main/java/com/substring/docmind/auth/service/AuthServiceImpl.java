package com.substring.docmind.auth.service;

import com.substring.docmind.auth.dto.LoginRequest;
import com.substring.docmind.auth.dto.LoginResponse;
import com.substring.docmind.auth.dto.RegisterRequest;
import com.substring.docmind.auth.dto.SocialLoginRequest;
import com.substring.docmind.enums.AuthProvider;
import com.substring.docmind.enums.Role;
import com.substring.docmind.exception.BadRequestException;
import com.substring.docmind.security.JwtService;
import com.substring.docmind.user.dto.UserDto;
import com.substring.docmind.user.entity.User;
import com.substring.docmind.user.mapper.UserMapper;
import com.substring.docmind.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public UserDto register(RegisterRequest request) {
        log.info("Processing user registration for username: {}, email: {}", request.getUsername(), request.getEmail());

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username '" + request.getUsername() + "' is already taken");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email '" + request.getEmail() + "' is already registered");
        }

        User user = User.builder()
                .username(request.getUsername().trim().toLowerCase())
                .email(request.getEmail().trim().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.ROLE_USER)
                .provider(AuthProvider.LOCAL)
                .enabled(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully with id: {}", savedUser.getId());
        return userMapper.toDto(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        log.info("Processing login for user: {}", request.getUsername());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername().trim().toLowerCase(),
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String token = jwtService.generateToken(authentication);

        User user = userRepository.findByUsernameOrEmail(request.getUsername(), request.getUsername())
                .orElseThrow(() -> new BadRequestException("User record not found"));

        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .user(userMapper.toDto(user))
                .build();
    }

    @Override
    @Transactional
    public LoginResponse socialLogin(SocialLoginRequest request) {
        log.info("Processing social login for email: {} with provider: {}", request.getEmail(), request.getProvider());

        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            String derivedUsername = request.getName() != null && !request.getName().isBlank()
                    ? request.getName().replaceAll("\\s+", "").toLowerCase()
                    : email.split("@")[0];

            int count = 1;
            String baseUsername = derivedUsername;
            while (userRepository.existsByUsername(derivedUsername)) {
                derivedUsername = baseUsername + count++;
            }

            User newUser = User.builder()
                    .username(derivedUsername)
                    .email(email)
                    .provider(request.getProvider())
                    .providerId(request.getProviderId())
                    .imageUrl(request.getImageUrl())
                    .role(Role.ROLE_USER)
                    .enabled(true)
                    .build();

            return userRepository.save(newUser);
        });

        String token = jwtService.generateTokenFromUsername(user.getUsername(), user.getId());

        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .user(userMapper.toDto(user))
                .build();
    }
}
