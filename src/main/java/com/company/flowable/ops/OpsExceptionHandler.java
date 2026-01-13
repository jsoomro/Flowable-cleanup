package com.company.flowable.ops;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OpsExceptionHandler {

    @ExceptionHandler(OpsException.class)
    public ResponseEntity<ApiError> handleOpsException(OpsException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ApiError(ex.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiError("Unexpected error", ex.getMessage()));
    }
}
