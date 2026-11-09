package com.suwon.festival.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> handleIlleagalAggument(IllegalArgumentException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
  }

  // 고유 제약 위반 (예: 탭 2개에서 같은 찜을 동시에 저장) → 500 대신 409. 먼저 저장한 쪽이 이미 반영됨
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<String> handleConflict(DataIntegrityViolationException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body("이미 처리된 요청입니다. 새로 고침해 주세요.");
  }
}
