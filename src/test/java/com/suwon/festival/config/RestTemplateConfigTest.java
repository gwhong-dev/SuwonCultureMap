package com.suwon.festival.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ServerSocket;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class RestTemplateConfigTest {

  @Test
  void 연결만_받고_응답하지_않는_서버는_시간_제한으로_끝남() throws Exception {
    try (ServerSocket silentServer = new ServerSocket(0)) {
      String url = "http://127.0.0.1:" + silentServer.getLocalPort() + "/";

      assertThatThrownBy(() -> RestTemplateConfig.create(Duration.ofSeconds(1), Duration.ofMillis(200))
          .getForObject(url, String.class))
          .isInstanceOf(ResourceAccessException.class);
    }
  }
}
