package com.hotel.solr.framework.mvc;

import com.hotel.solr.framework.exception.BusinessException;
import com.hotel.solr.framework.lang.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * @author gaofeng
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBusinessException(BusinessException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResult.error(ex.getStatus(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        return badRequest(collectFieldErrors(ex.getBindingResult().getFieldErrors()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResult<Void>> handleBindException(BindException ex) {
        return badRequest(collectFieldErrors(ex.getBindingResult().getFieldErrors()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResult<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return badRequest(message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResult<Void>> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        return badRequest("请求体格式错误");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleUnexpected(Exception ex) {
        log.error("未处理的异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.error(500, "服务器内部错误"));
    }

    private ResponseEntity<ApiResult<Void>> badRequest(String message) {
        return ResponseEntity.badRequest().body(ApiResult.error(400, message));
    }

    private String collectFieldErrors(Iterable<FieldError> fieldErrors) {
        StringBuilder sb = new StringBuilder();
        for (FieldError error : fieldErrors) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(error.getField()).append(": ").append(error.getDefaultMessage());
        }
        return sb.toString();
    }
}
