package com.example.purchaserequest.controller;

import com.example.purchaserequest.exception.InvalidStatusTransitionException;
import com.example.purchaserequest.exception.RequestNotFoundException;
import com.example.purchaserequest.exception.UnauthorizedOperationException;
import com.example.purchaserequest.model.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStatusTransition(InvalidStatusTransitionException ex) {
        ErrorResponse error = ErrorResponse.builder()
            .status("error")
            .error(ErrorResponse.ErrorDetail.builder()
                .code("INVALID_STATUS_TRANSITION")
                .message(ex.getMessage())
                .build())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(UnauthorizedOperationException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedOperation(UnauthorizedOperationException ex) {
        ErrorResponse error = ErrorResponse.builder()
            .status("error")
            .error(ErrorResponse.ErrorDetail.builder()
                .code("FORBIDDEN")
                .message(ex.getMessage())
                .build())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(RequestNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRequestNotFound(RequestNotFoundException ex) {
        ErrorResponse error = ErrorResponse.builder()
            .status("error")
            .error(ErrorResponse.ErrorDetail.builder()
                .code("NOT_FOUND")
                .message(ex.getMessage())
                .build())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        List<ErrorResponse.ErrorDetail.FieldError> details = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ErrorResponse.ErrorDetail.FieldError(
                fe.getField(),
                fe.getDefaultMessage()
            ))
            .toList();

        ErrorResponse error = ErrorResponse.builder()
            .status("error")
            .error(ErrorResponse.ErrorDetail.builder()
                .code("VALIDATION_ERROR")
                .message("入力値が不正です")
                .details(details)
                .build())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        String code = ex.getMessage() != null && ex.getMessage().contains("既に削除")
            ? "ALREADY_DELETED"
            : "INVALID_STATUS_TRANSITION";
        ErrorResponse error = ErrorResponse.builder()
            .status("error")
            .error(ErrorResponse.ErrorDetail.builder()
                .code(code)
                .message(ex.getMessage())
                .build())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ErrorResponse error = ErrorResponse.builder()
            .status("error")
            .error(ErrorResponse.ErrorDetail.builder()
                .code("VALIDATION_ERROR")
                .message(ex.getMessage())
                .build())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("予期しないエラーが発生しました", ex);
        ErrorResponse error = ErrorResponse.builder()
            .status("error")
            .error(ErrorResponse.ErrorDetail.builder()
                .code("INTERNAL_SERVER_ERROR")
                .message("サーバー内部エラーが発生しました")
                .build())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
