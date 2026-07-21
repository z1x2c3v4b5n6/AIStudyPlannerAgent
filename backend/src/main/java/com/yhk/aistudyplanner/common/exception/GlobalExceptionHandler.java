package com.yhk.aistudyplanner.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.yhk.aistudyplanner.goal.entity.GoalStatus;
import com.yhk.aistudyplanner.task.entity.TaskStatus;
import com.yhk.aistudyplanner.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception) {
        return error(exception.getErrorCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(code.status())
                .body(new ApiResponse<>(code.code(), code.message(), errors, System.currentTimeMillis()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        return error(ErrorCode.VALIDATION_ERROR);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        if (exception.getRequiredType() == GoalStatus.class) return error(ErrorCode.INVALID_GOAL_STATUS);
        if (exception.getRequiredType() == TaskStatus.class) return error(ErrorCode.INVALID_TASK_STATUS);
        return error(ErrorCode.VALIDATION_ERROR);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException exception) {
        return error(ErrorCode.VALIDATION_ERROR);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof InvalidFormatException invalidFormat) {
            if (invalidFormat.getTargetType() == GoalStatus.class) return error(ErrorCode.INVALID_GOAL_STATUS);
            if (invalidFormat.getTargetType() == TaskStatus.class) return error(ErrorCode.INVALID_TASK_STATUS);
        }
        return error(ErrorCode.VALIDATION_ERROR);
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotLogin(NotLoginException exception) {
        return error(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleRedis(RedisConnectionFailureException exception) {
        log.error("Redis connection failure", exception);
        return error(ErrorCode.REDIS_UNAVAILABLE);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateKey(DuplicateKeyException exception) {
        log.warn("Duplicate database key rejected");
        return error(ErrorCode.DATA_CONFLICT);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDatabase(DataAccessException exception) {
        log.error("Database access failure", exception);
        return error(ErrorCode.DATABASE_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception exception) {
        log.error("Unhandled server error", exception);
        return error(ErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> error(ErrorCode code) {
        return ResponseEntity.status(code.status()).body(ApiResponse.error(code.code(), code.message()));
    }
}
