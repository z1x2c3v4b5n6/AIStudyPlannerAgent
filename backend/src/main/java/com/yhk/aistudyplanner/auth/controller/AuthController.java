package com.yhk.aistudyplanner.auth.controller;

import com.yhk.aistudyplanner.auth.dto.LoginRequest;
import com.yhk.aistudyplanner.auth.dto.RegisterRequest;
import com.yhk.aistudyplanner.auth.service.AuthService;
import com.yhk.aistudyplanner.auth.vo.LoginView;
import com.yhk.aistudyplanner.auth.vo.UserView;
import com.yhk.aistudyplanner.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<UserView> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginView> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    public ApiResponse<UserView> me() {
        return ApiResponse.success(authService.currentUser());
    }
}
