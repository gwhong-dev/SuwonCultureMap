package com.suwon.festival.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

  // 응답 없는 외부 서버에 자동 동기화 · 로그인 요청이 무한정 묶이지 않게 제한
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

  @Bean
  public RestTemplate restTemplate() {
    return create(CONNECT_TIMEOUT, READ_TIMEOUT);
  }

  // 짧은 제한이 필요한 클라이언트(화면 열람 때 부르는 경기도 정류소 API 등)도 같은 방식으로 만듦
  public static RestTemplate create(Duration connectTimeout, Duration readTimeout) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout);
    factory.setReadTimeout(readTimeout);
    return new RestTemplate(factory);
  }
}
