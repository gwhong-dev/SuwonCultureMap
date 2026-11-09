package com.suwon.festival.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 비회원 찜 합치기(POST /api/favorites/merge) 본문 크기 상한.
 * 화면은 행사 번호 최대 100개(약 2KB)만 보냄 → 그보다 훨씬 큰 본문은 JSON 을 읽기 전에 413 으로 거절.
 */
@Component
public class MergeBodyLimitFilter extends OncePerRequestFilter {

  static final String PATH = "/api/favorites/merge";
  static final long MAX_BYTES = 8 * 1024;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !PATH.equals(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > MAX_BYTES) {
      response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
      response.setContentType("text/plain;charset=UTF-8");
      response.getWriter().write("합칠 찜이 너무 많습니다.");
      return;
    }
    chain.doFilter(request, response);
  }
}
