package com.substring.docmind.user.controller;

import com.substring.docmind.common.ApiResponse;
import com.substring.docmind.security.UserPrincipal;
import com.substring.docmind.user.dto.UserDto;
import com.substring.docmind.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Endpoints for managing user accounts, deleting accounts, and retrieving profiles")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get current user profile", description = "Retrieves authenticated user details from security context")
    public ResponseEntity<ApiResponse<UserDto>> getCurrentUser(@AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("REST request to get profile for authenticated user id: {}", currentUser.getId());
        UserDto profile = userService.getCurrentUserProfile(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @GetMapping
    @Operation(summary = "Get all users", description = "Retrieves list of all registered users (Admin access)")
    public ResponseEntity<ApiResponse<List<UserDto>>> getAllUsers(@AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("REST request to list all users by user id: {}", currentUser.getId());
        List<UserDto> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @DeleteMapping("/me")
    @Operation(summary = "Delete own account", description = "Permanently deletes authenticated user account and all associated data from database")
    public ResponseEntity<ApiResponse<Void>> deleteOwnAccount(@AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("REST request for user id {} to delete their own account", currentUser.getId());
        userService.deleteUser(currentUser.getId(), currentUser);
        return ResponseEntity.ok(ApiResponse.success("User account and all associated data deleted successfully from database", null));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user by ID", description = "Permanently deletes user account and all associated data from database (Admin or account owner)")
    public ResponseEntity<ApiResponse<Void>> deleteUserById(
            @PathVariable("id") Long userId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to delete user id {} by operator id: {}", userId, currentUser.getId());
        userService.deleteUser(userId, currentUser);
        return ResponseEntity.ok(ApiResponse.success("User id " + userId + " and all associated data deleted successfully from database", null));
    }
}
