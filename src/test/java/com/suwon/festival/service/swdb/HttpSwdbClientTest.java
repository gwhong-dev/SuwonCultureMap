package com.suwon.festival.service.swdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

// 수원문화DB 요청 — robots.txt 허용 주소만 · 서비스 이름 User-Agent · 요청 사이 쉼 (실제 요청 없음)
class HttpSwdbClientTest {

  final RestTemplate restTemplate = new RestTemplate();
  final MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
  final AtomicInteger pauses = new AtomicInteger();
  final HttpSwdbClient client = new HttpSwdbClient(restTemplate, pauses::incrementAndGet);

  @Test
  void 목록_상세_주소와_User_Agent_그리고_두번째부터_쉼() {
    server.expect(requestTo("https://www.swcf.or.kr/swdb/?p=15&page=2"))
        .andExpect(header(HttpHeaders.USER_AGENT, HttpSwdbClient.USER_AGENT))
        .andRespond(withSuccess("목록".getBytes(StandardCharsets.UTF_8), MediaType.TEXT_HTML));
    server.expect(requestTo("https://www.swcf.or.kr/swdb/?p=15_view&reqIdx=202611061144388032&page=1"))
        .andExpect(header(HttpHeaders.USER_AGENT, HttpSwdbClient.USER_AGENT))
        .andRespond(withSuccess("상세".getBytes(StandardCharsets.UTF_8), MediaType.TEXT_HTML));

    assertThat(client.listPage(2)).isEqualTo("목록");
    assertThat(pauses.get()).isZero();
    assertThat(client.detailPage("202611061144388032")).isEqualTo("상세");
    assertThat(pauses.get()).isEqualTo(1);
    server.verify();
  }

  @Test
  void 숫자가_아닌_키로는_요청하지_않음_검색_주소를_만들_수_없음() {
    assertThatThrownBy(() -> client.detailPage("1&searchTxt=x")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> client.listPage(0)).isInstanceOf(IllegalArgumentException.class);
    server.verify(); // 요청 0번
  }
}
