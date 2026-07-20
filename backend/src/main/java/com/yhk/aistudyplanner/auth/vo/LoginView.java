package com.yhk.aistudyplanner.auth.vo;

public record LoginView(String tokenName, String tokenValue, long timeout, UserView user) {
}

