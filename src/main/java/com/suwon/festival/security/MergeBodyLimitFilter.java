package com.suwon.festival.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 비회원 찜 합치기(POST /api/favorites/merge) 본문 크기 상한.
 * 화면은 행사 번호 최대 100개(약 2KB)만 보냄 → 그보다 훨씬 큰 본문은 거절.
 * - 길이를 밝힌 요청: JSON 을 읽기 전에 413
 * - 길이를 안 밝힌 요청(chunked): 읽는 도중 상한을 넘으면 끊음 → JSON 읽기 실패 400
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
    chain.doFilter(new LimitedRequest(request), response);
  }

  // 읽은 바이트를 세다가 상한을 넘으면 IOException
  private static class LimitedRequest extends HttpServletRequestWrapper {
    private ServletInputStream limited;

    LimitedRequest(HttpServletRequest request) {
      super(request);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      if (limited == null) limited = new LimitedInputStream(super.getInputStream());
      return limited;
    }
  }

  private static class LimitedInputStream extends ServletInputStream {
    private final ServletInputStream in;
    private long count;

    LimitedInputStream(ServletInputStream in) {
      this.in = in;
    }

    private int counted(int n) throws IOException {
      if (n > 0 && (count += n) > MAX_BYTES) throw new IOException("합칠 찜 본문이 너무 큽니다.");
      return n;
    }

    @Override
    public int read() throws IOException {
      int b = in.read();
      if (b != -1) counted(1);
      return b;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
      return counted(in.read(buf, off, len));
    }

    @Override
    public boolean isFinished() {
      return in.isFinished();
    }

    @Override
    public boolean isReady() {
      return in.isReady();
    }

    @Override
    public void setReadListener(ReadListener listener) {
      in.setReadListener(listener);
    }
  }
}
