package com.yhk.aistudyplanner.auth.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yhk.aistudyplanner.auth.dto.LoginRequest;
import com.yhk.aistudyplanner.auth.dto.RegisterRequest;
import com.yhk.aistudyplanner.auth.entity.User;
import com.yhk.aistudyplanner.auth.mapper.UserMapper;
import com.yhk.aistudyplanner.auth.vo.LoginView;
import com.yhk.aistudyplanner.auth.vo.UserView;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {
    private static final int USER_ENABLED = 1;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService sessionService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, AuthSessionService sessionService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
    }

    @Transactional
    public UserView register(RegisterRequest request) {
        User existing = findByUsername(request.username());
        if (existing != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setStatus(USER_ENABLED);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        return UserView.from(user);
    }

    public LoginView login(LoginRequest request) {
        User user = findByUsername(request.username());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!Integer.valueOf(USER_ENABLED).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        SaTokenInfo tokenInfo = sessionService.login(user.getId());
        return new LoginView(tokenInfo.getTokenName(), tokenInfo.getTokenValue(), tokenInfo.getTokenTimeout(), UserView.from(user));
    }

    public void logout() {
        sessionService.logout();
    }

    public UserView currentUser() {
        User user = userMapper.selectById(sessionService.currentUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return UserView.from(user);
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }
}

