package com.yhk.aistudyplanner.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(40000, "参数校验失败", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40100, "未登录或登录已失效", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(40101, "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    USER_DISABLED(40300, "用户已被禁用", HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(40400, "资源不存在", HttpStatus.NOT_FOUND),
    USERNAME_EXISTS(40900, "用户名已存在", HttpStatus.CONFLICT),
    REDIS_UNAVAILABLE(50300, "登录服务暂时不可用", HttpStatus.SERVICE_UNAVAILABLE),
    DATABASE_ERROR(50001, "数据库服务异常", HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_ERROR(50000, "系统内部异常", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(int code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public int code() { return code; }
    public String message() { return message; }
    public HttpStatus status() { return status; }
}

