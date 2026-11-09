package com.substring.docmind.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HomeController {

    @GetMapping({"/", "/health", "/api/v1/health"})
    public ResponseEntity<ApiResponse<Map<String, Object>>> home() {
        return ResponseEntity.ok(ApiResponse.success(
                "DocMind Backend API is operational",
                Map.of(
                        "status", "UP",
                        "swagger", "/swagger-ui.html",
                        "apiDocs", "/v3/api-docs"
                )
        ));
    }

    @GetMapping("favicon.ico")
    public ResponseEntity<Void> returnNoFavicon() {
        return ResponseEntity.noContent().build();
    }
}
