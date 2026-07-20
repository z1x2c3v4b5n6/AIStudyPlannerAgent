package com.yhk.aistudyplanner.auth.vo;

import com.yhk.aistudyplanner.auth.entity.User;

import java.time.LocalDateTime;

public record UserView(Long id, String username, String nickname, LocalDateTime createdAt) {
    public static UserView from(User user) {
        return new UserView(user.getId(), user.getUsername(), user.getNickname(), user.getCreatedAt());
    }
}

