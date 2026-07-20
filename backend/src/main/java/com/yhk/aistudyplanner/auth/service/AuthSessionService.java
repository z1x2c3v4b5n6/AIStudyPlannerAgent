package com.yhk.aistudyplanner.auth.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

@Component
public class AuthSessionService {

    public SaTokenInfo login(Long userId) {
        StpUtil.login(userId);
        return StpUtil.getTokenInfo();
    }

    public void logout() {
        StpUtil.logout();
    }

    public long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }
}

