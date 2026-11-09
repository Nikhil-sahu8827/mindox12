package com.substring.docmind.auth;

import com.substring.docmind.auth.dto.LoginRequest;
import com.substring.docmind.auth.dto.LoginResponse;
import com.substring.docmind.auth.dto.RegisterRequest;
import com.substring.docmind.auth.dto.SocialLoginRequest;
import com.substring.docmind.auth.service.AuthServiceImpl;
import com.substring.docmind.enums.AuthProvider;
import com.substring.docmind.enums.Role;
import com.substring.docmind.exception.BadRequestException;
import com.substring.docmind.security.JwtService;
import com.substring.docmind.user.dto.UserDto;
import com.substring.docmind.user.entity.User;
import com.substring.docmind.user.mapper.UserMapper;
import com.substring.docmind.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthServiceImpl authService;

    private User sampleUser;
    private UserDto sampleUserDto;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password("encoded_pass")
                .role(Role.ROLE_USER)
                .provider(AuthProvider.LOCAL)
                .enabled(true)
                .build();

        sampleUserDto = UserDto.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .role(Role.ROLE_USER)
                .provider(AuthProvider.LOCAL)
                .build();
    }

    @Test
    void register_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .username("newuser")
                .email("new@example.com")
                .password("password123")
                .build();

        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed_pass");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(userMapper.toDto(sampleUser)).thenReturn(sampleUserDto);

        UserDto result = authService.register(request);

        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void register_DuplicateUsername_ThrowsBadRequestException() {
        RegisterRequest request = RegisterRequest.builder()
                .username("testuser")
                .email("other@example.com")
                .password("password123")
                .build();

        when(userRepository.existsByUsername(request.getUsername())).thenReturn(true);

        assertThrows(BadRequestException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_Success() {
        LoginRequest request = LoginRequest.builder()
                .username("testuser")
                .password("password123")
                .build();

        Authentication auth = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(jwtService.generateToken(auth)).thenReturn("mock_jwt_token");
        when(userRepository.findByUsernameOrEmail("testuser", "testuser")).thenReturn(Optional.of(sampleUser));
        when(userMapper.toDto(sampleUser)).thenReturn(sampleUserDto);

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock_jwt_token", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals("testuser", response.getUser().getUsername());
    }

    @Test
    void socialLogin_ExistingUser_GeneratesToken() {
        SocialLoginRequest request = SocialLoginRequest.builder()
                .provider(AuthProvider.GOOGLE)
                .email("test@example.com")
                .name("Test User")
                .providerId("google_123")
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(sampleUser));
        when(jwtService.generateTokenFromUsername("testuser", 1L)).thenReturn("social_jwt_token");
        when(userMapper.toDto(sampleUser)).thenReturn(sampleUserDto);

        LoginResponse response = authService.socialLogin(request);

        assertNotNull(response);
        assertEquals("social_jwt_token", response.getAccessToken());
        verify(userRepository, never()).save(any(User.class));
    }
}
