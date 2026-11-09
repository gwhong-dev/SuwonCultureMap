package com.suwon.festival.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

// 길이를 밝히지 않은(chunked) 본문도 읽는 도중 상한에서 끊는지
class MergeBodyLimitFilterTest {

  private MockHttpServletRequest chunked(int bytes) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", MergeBodyLimitFilter.PATH) {
      @Override
      public long getContentLengthLong() {
        return -1; // Content-Length 없음
      }
    };
    request.setContent(new byte[bytes]);
    return request;
  }

  @Test
  void 길이_없는_큰_본문은_읽다가_끊음() {
    assertThatThrownBy(() -> new MergeBodyLimitFilter().doFilter(chunked(10_000), new MockHttpServletResponse(),
        (req, res) -> req.getInputStream().readAllBytes()))
        .isInstanceOf(IOException.class);
  }

  @Test
  void 길이_없는_작은_본문은_그대로() throws Exception {
    int[] read = new int[1];
    new MergeBodyLimitFilter().doFilter(chunked(2_000), new MockHttpServletResponse(),
        (req, res) -> read[0] = req.getInputStream().readAllBytes().length);
    assertThat(read[0]).isEqualTo(2_000);
  }
}
