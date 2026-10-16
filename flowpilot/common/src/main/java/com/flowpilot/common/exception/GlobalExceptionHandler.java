package com.flowpilot.common.exception;

import com.flowpilot.common.dto.ApiResponse;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(BusinessException.class)
  ResponseEntity<?> business(BusinessException e) {
    return ResponseEntity.status(e.status)
        .body(new ApiResponse<>(false, null, e.code, e.getMessage()));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    IllegalArgumentException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class
  })
  ResponseEntity<?> invalid(Exception e) {
    return ResponseEntity.badRequest()
        .body(new ApiResponse<>(false, null, "VALIDATION_ERROR", "请求参数无效"));
  }

  @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
  ResponseEntity<?> duplicate(Exception e) {
    return ResponseEntity.status(409)
        .body(new ApiResponse<>(false, null, "CONFLICT", "记录已存在或已被处理"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unknown(Exception e) {
    org.slf4j.LoggerFactory.getLogger(getClass())
        .error("Unhandled failure type={}", e.getClass().getSimpleName());
    return ResponseEntity.status(500)
        .body(new ApiResponse<>(false, null, "INTERNAL_ERROR", "服务异常，请通过关联记录检查"));
  }
}
