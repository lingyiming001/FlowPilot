package com.flowpilot.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
  public final String code;
  public final HttpStatus status;
  public final boolean retryable;

  public BusinessException(String code, String message, HttpStatus status) {
    this(code, message, status, false);
  }

  public BusinessException(String code, String message, HttpStatus status, boolean retryable) {
    super(message);
    this.code = code;
    this.status = status;
    this.retryable = retryable;
  }

  public static BusinessException conflict(String message) {
    return new BusinessException("CONFLICT", message, HttpStatus.CONFLICT);
  }

  public static BusinessException denied() {
    return new BusinessException("FORBIDDEN", "无权访问此资源", HttpStatus.FORBIDDEN);
  }

  public static BusinessException invalid(String message) {
    return new BusinessException("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
  }

  public static BusinessException missing() {
    return new BusinessException("NOT_FOUND", "资源不存在或不在访问范围", HttpStatus.NOT_FOUND);
  }
}
