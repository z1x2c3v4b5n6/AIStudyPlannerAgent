package com.yhk.aistudyplanner.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(40000, "参数校验失败", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40100, "未登录或登录已失效", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(40101, "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    USER_DISABLED(40300, "用户已被禁用", HttpStatus.FORBIDDEN),
    SUBJECT_ACCESS_DENIED(40310, "无权访问该学习科目", HttpStatus.FORBIDDEN),
    GOAL_ACCESS_DENIED(40311, "无权访问该学习目标", HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(40400, "资源不存在", HttpStatus.NOT_FOUND),
    SUBJECT_NOT_FOUND(40410, "学习科目不存在", HttpStatus.NOT_FOUND),
    GOAL_NOT_FOUND(40411, "学习目标不存在", HttpStatus.NOT_FOUND),
    TASK_NOT_FOUND(40412, "学习任务不存在", HttpStatus.NOT_FOUND),
    USERNAME_EXISTS(40900, "用户名已存在", HttpStatus.CONFLICT),
    DATA_CONFLICT(40901, "数据冲突，请刷新后重试", HttpStatus.CONFLICT),
    SUBJECT_NAME_EXISTS(40910, "同名学习科目已存在", HttpStatus.CONFLICT),
    SUBJECT_HAS_REFERENCES(40911, "该科目存在关联目标、任务或学习记录，无法删除", HttpStatus.CONFLICT),
    GOAL_HAS_TASKS(40912, "该目标存在关联任务，无法删除", HttpStatus.CONFLICT),
    TASK_HAS_REFERENCES(40913, "该任务存在学习记录或计划项，无法删除", HttpStatus.CONFLICT),
    GOAL_SUBJECT_MISMATCH(40914, "学习目标所属科目与任务科目不一致", HttpStatus.CONFLICT),
    INVALID_GOAL_STATUS(40010, "学习目标状态无效", HttpStatus.BAD_REQUEST),
    INVALID_TASK_STATUS(40011, "学习任务状态无效", HttpStatus.BAD_REQUEST),
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

