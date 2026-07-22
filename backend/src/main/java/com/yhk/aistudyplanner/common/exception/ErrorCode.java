package com.yhk.aistudyplanner.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(40000, "参数校验失败", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40100, "未登录或登录已失效", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(40101, "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    USER_DISABLED(40300, "用户已被禁用", HttpStatus.FORBIDDEN),
    SUBJECT_ACCESS_DENIED(40310, "无权访问该学习科目", HttpStatus.FORBIDDEN),
    GOAL_ACCESS_DENIED(40311, "无权访问该学习目标", HttpStatus.FORBIDDEN),
    RECORD_ACCESS_DENIED(40312, "无权访问该学习记录", HttpStatus.FORBIDDEN),
    TASK_ACCESS_DENIED(40313, "无权访问该学习任务", HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(40400, "资源不存在", HttpStatus.NOT_FOUND),
    SUBJECT_NOT_FOUND(40410, "学习科目不存在", HttpStatus.NOT_FOUND),
    GOAL_NOT_FOUND(40411, "学习目标不存在", HttpStatus.NOT_FOUND),
    TASK_NOT_FOUND(40412, "学习任务不存在", HttpStatus.NOT_FOUND),
    RECORD_NOT_FOUND(40413, "学习记录不存在", HttpStatus.NOT_FOUND),
    USERNAME_EXISTS(40900, "用户名已存在", HttpStatus.CONFLICT),
    DATA_CONFLICT(40901, "数据冲突，请刷新后重试", HttpStatus.CONFLICT),
    SUBJECT_NAME_EXISTS(40910, "同名学习科目已存在", HttpStatus.CONFLICT),
    SUBJECT_HAS_REFERENCES(40911, "该科目存在关联目标、任务或学习记录，无法删除", HttpStatus.CONFLICT),
    GOAL_HAS_TASKS(40912, "该目标存在关联任务，无法删除", HttpStatus.CONFLICT),
    TASK_HAS_REFERENCES(40913, "该任务存在学习记录或计划项，无法删除", HttpStatus.CONFLICT),
    GOAL_SUBJECT_MISMATCH(40914, "学习目标所属科目与任务科目不一致", HttpStatus.CONFLICT),
    INVALID_GOAL_STATUS(40010, "学习目标状态无效", HttpStatus.BAD_REQUEST),
    INVALID_TASK_STATUS(40011, "学习任务状态无效", HttpStatus.BAD_REQUEST),
    RECORD_SUBJECT_MISMATCH(40012, "学习任务所属科目与学习记录科目不一致", HttpStatus.BAD_REQUEST),
    INVALID_RECORD_TIME(40013, "学习记录结束时间必须晚于开始时间", HttpStatus.BAD_REQUEST),
    INVALID_RECORD_DURATION(40014, "单次学习时长必须在1至1440分钟之间", HttpStatus.BAD_REQUEST),
    RECORD_END_TIME_IN_FUTURE(40015, "学习记录结束时间不能晚于当前时间", HttpStatus.BAD_REQUEST),
    INVALID_DATE_RANGE(40016, "开始日期不能晚于结束日期", HttpStatus.BAD_REQUEST),
    DATE_RANGE_TOO_LARGE(40017, "统计日期范围不能超过366天", HttpStatus.BAD_REQUEST),
    RECORD_CROSSES_DAY(40018, "学习记录不能跨越自然日", HttpStatus.BAD_REQUEST),
    RECORD_TIME_OVERLAP(40915, "学习记录时间与已有记录重叠", HttpStatus.CONFLICT),
    PLAN_DRAFT_EMPTY(40020, "计划草案不能为空", HttpStatus.BAD_REQUEST),
    PLAN_DATE_IN_PAST(40021, "不能为过去日期生成学习计划", HttpStatus.BAD_REQUEST),
    INVALID_PLAN_TIME(40022, "计划项时间不合法或不在计划日期内", HttpStatus.BAD_REQUEST),
    INVALID_PLAN_DURATION(40023, "计划时长与时间段不一致或超过可用时长", HttpStatus.BAD_REQUEST),
    PLAN_TIME_OVERLAP(40024, "计划项时间存在重叠", HttpStatus.BAD_REQUEST),
    PLAN_TASK_DUPLICATED(40025, "同一任务不能在计划中重复出现", HttpStatus.BAD_REQUEST),
    PLAN_TASK_INVALID(40026, "计划任务不存在、无权访问或状态不可用", HttpStatus.BAD_REQUEST),
    PLAN_NOT_FOUND(40420, "学习计划不存在", HttpStatus.NOT_FOUND),
    PLAN_ACCESS_DENIED(40320, "无权访问该学习计划", HttpStatus.FORBIDDEN),
    PLAN_ITEM_NOT_FOUND(40421, "学习计划项不存在", HttpStatus.NOT_FOUND),
    PLAN_ALREADY_CANCELLED(40920, "学习计划已取消", HttpStatus.CONFLICT),
    INVALID_PLAN_STATUS_TRANSITION(40921, "不允许执行该计划状态变更", HttpStatus.CONFLICT),
    INVALID_PLAN_ITEM_STATUS_TRANSITION(40922, "不允许执行该计划项状态变更", HttpStatus.CONFLICT),
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

