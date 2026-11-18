package com.hrapp.controller;

import com.hrapp.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Lightweight public probe used by uptime / keep-warm pingers.
 * No DB access — must stay cheap so cold-start wake-ups are fast.
 */
@RestController
@RequestMapping("/health")
@Tag(name = "0. Health")
public class HealthController {

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of(
                        "status", "UP",
                        "timestamp", Instant.now().toString()
                ),
                "OK"));
    }
}
