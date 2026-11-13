package com.suwon.festival.service.landmark;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

// 재단 명소 API 호출 (행사 29_EV 와 같은 형식: ?CG=코드&openAPIKey=키)
//  - 인증키는 코드마다 다름: 59_C = swcf.api.key.facility, 63_C = swcf.api.key.palace-facility (운영 환경변수 SWCF_API_KEY_FACILITY · SWCF_API_KEY_PALACE_FACILITY)
//  - 키가 비어 있으면 그 코드는 받지 않음. 호출 주소에 키가 들어 있으므로 예외 메시지는 남기지 말 것 (부르는 쪽이 종류만 기록)
@Component
public class LandmarkClient {

  static final String BASE_URL = "http://www.swcf.or.kr/openAPI/?CG=";

  private final RestTemplate restTemplate;
  private final String fortressKey;
  private final String palaceKey;

  public LandmarkClient(RestTemplate restTemplate,
      @Value("${swcf.api.key.facility:}") String fortressKey,
      @Value("${swcf.api.key.palace-facility:}") String palaceKey) {
    this.restTemplate = restTemplate;
    this.fortressKey = fortressKey;
    this.palaceKey = palaceKey;
  }

  public boolean configured(LandmarkFeed feed) {
    String key = key(feed);
    return key != null && !key.isBlank();
  }

  public String fetch(LandmarkFeed feed) {
    if (!configured(feed)) {
      throw new IllegalStateException(feed.code + " 인증키 없음");
    }
    return restTemplate.getForObject(BASE_URL + feed.code + "&openAPIKey=" + key(feed).strip(), String.class);
  }

  private String key(LandmarkFeed feed) {
    return switch (feed) {
      case FORTRESS -> fortressKey;
      case PALACE -> palaceKey;
    };
  }
}
