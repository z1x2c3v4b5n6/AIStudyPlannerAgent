package com.yhk.aistudyplanner.config;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.spring.SaTokenContextRegister;
import com.yhk.aistudyplanner.auth.controller.AuthController;
import com.yhk.aistudyplanner.auth.vo.LoginView;
import com.yhk.aistudyplanner.auth.service.AuthService;
import com.yhk.aistudyplanner.auth.vo.UserView;
import com.yhk.aistudyplanner.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SaTokenConfig.class, WebConfig.class, GlobalExceptionHandler.class, SaTokenContextRegister.class})
class AuthCorsIntegrationTest {
    private static final String ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void preflightForProtectedEndpointSkipsLoginCheckAndContainsCorsHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/auth/me")
                        .header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Content-Type,satoken"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("GET")))
                .andExpect(header().string("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsStringIgnoringCase("satoken")));
    }

    @Test
    void unauthenticatedGetRemainsProtected() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Origin", ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void authenticatedGetReturnsCurrentUser() throws Exception {
        UserView user = new UserView(1L, "testuser", "测试用户", LocalDateTime.now());
        when(authService.currentUser()).thenReturn(user);
        when(authService.login(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            StpUtil.login(1L);
            return new LoginView(StpUtil.getTokenName(), StpUtil.getTokenValue(), StpUtil.getTokenTimeout(), user);
        });
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"testuser\",\"password\":\"Test123456\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(loginResponse).path("data").path("tokenValue").asText();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Origin", ORIGIN)
                        .header(StpUtil.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(jsonPath("$.data.username").value("testuser"));
    }
}
