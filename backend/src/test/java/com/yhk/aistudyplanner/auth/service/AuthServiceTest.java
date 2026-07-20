package com.yhk.aistudyplanner.auth.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.yhk.aistudyplanner.auth.dto.LoginRequest;
import com.yhk.aistudyplanner.auth.dto.RegisterRequest;
import com.yhk.aistudyplanner.auth.entity.User;
import com.yhk.aistudyplanner.auth.mapper.UserMapper;
import com.yhk.aistudyplanner.auth.vo.LoginView;
import com.yhk.aistudyplanner.auth.vo.UserView;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock private UserMapper userMapper;
    @Mock private AuthSessionService sessionService;
    private BCryptPasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(userMapper, passwordEncoder, sessionService);
    }

    @Test
    void registersWithHashedPasswordAndNoHashInView() {
        when(userMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return 1;
        }).when(userMapper).insert(any(User.class));

        UserView view = authService.register(new RegisterRequest("testuser", "Test123456", "测试用户"));

        assertEquals(1L, view.id());
        verify(userMapper).insert(argThat((User user) ->
                !"Test123456".equals(user.getPasswordHash())
                        && passwordEncoder.matches("Test123456", user.getPasswordHash())));
    }

    @Test
    void rejectsExistingUsername() {
        when(userMapper.selectOne(any())).thenReturn(enabledUser());
        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.register(new RegisterRequest("testuser", "Test123456", "测试用户")));
        assertEquals(ErrorCode.USERNAME_EXISTS, exception.getErrorCode());
    }

    @Test
    void convertsConcurrentDuplicateUsernameToBusinessError() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenThrow(new DuplicateKeyException("duplicate"));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.register(new RegisterRequest("testuser", "Test123456", "测试用户")));
        assertEquals(ErrorCode.USERNAME_EXISTS, exception.getErrorCode());
    }

    @Test
    void logsInEnabledUser() {
        User user = enabledUser();
        when(userMapper.selectOne(any())).thenReturn(user);
        SaTokenInfo tokenInfo = new SaTokenInfo();
        tokenInfo.setTokenName("satoken");
        tokenInfo.setTokenValue("token-value");
        tokenInfo.setTokenTimeout(3600L);
        when(sessionService.login(1L)).thenReturn(tokenInfo);

        LoginView result = authService.login(new LoginRequest("testuser", "Test123456"));

        assertEquals("token-value", result.tokenValue());
        assertEquals("测试用户", result.user().nickname());
    }

    @Test
    void rejectsWrongPasswordWithoutRevealingUsernameState() {
        when(userMapper.selectOne(any())).thenReturn(enabledUser());
        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("testuser", "wrong-password")));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
        verifyNoInteractions(sessionService);
    }

    @Test
    void rejectsDisabledUser() {
        User user = enabledUser();
        user.setStatus(0);
        when(userMapper.selectOne(any())).thenReturn(user);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("testuser", "Test123456")));
        assertEquals(ErrorCode.USER_DISABLED, exception.getErrorCode());
    }

    @Test
    void doesNotReturnSuccessWhenRedisLoginFails() {
        when(userMapper.selectOne(any())).thenReturn(enabledUser());
        when(sessionService.login(1L)).thenThrow(new RedisConnectionFailureException("unavailable"));
        assertThrows(RedisConnectionFailureException.class,
                () -> authService.login(new LoginRequest("testuser", "Test123456")));
    }

    @Test
    void getsCurrentUserFromSessionId() {
        when(sessionService.currentUserId()).thenReturn(1L);
        when(userMapper.selectById(1L)).thenReturn(enabledUser());
        assertEquals("测试用户", authService.currentUser().nickname());
    }

    private User enabledUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPasswordHash(passwordEncoder.encode("Test123456"));
        user.setNickname("测试用户");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }
}
