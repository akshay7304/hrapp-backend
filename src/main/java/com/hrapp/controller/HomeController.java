package com.hrapp.controller;

import com.hrapp.ApiResponse;
import com.hrapp.dto.response.HomeResponse;
import com.hrapp.service.HomeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Single home-screen API. Role is taken from the JWT; response includes
 * {@code me} for everyone with a company, and {@code ops} for ADMIN/HR.
 */
@RestController
@RequestMapping("/home")
@RequiredArgsConstructor
@Tag(name = "15. Home")
public class HomeController {

    private final HomeService homeService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<HomeResponse>> getHome() {
        HomeResponse data = homeService.getHome();
        return ResponseEntity.ok(ApiResponse.success(data, "Home retrieved successfully"));
    }
}
