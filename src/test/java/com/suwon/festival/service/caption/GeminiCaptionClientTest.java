package com.suwon.festival.service.caption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Gemini 호출 모양 — 가짜 서버(MockRestServiceServer)로 확인, 실제 호출 없음
// 응답 모양은 2026-11-24 공식 API 레퍼런스(Interactions API) 예시 기준
class GeminiCaptionClientTest {

  private static final String KEY = "test-gemini-key";
  private static final String MODEL = "gemini-3.5-flash-lite";
  private static final ObjectMapper JSON = new ObjectMapper();

  private MockRestServiceServer server;
  private GeminiCaptionClient client;

  @BeforeEach
  void setUp() {
    RestTemplate restTemplate = new RestTemplate();
    server = MockRestServiceServer.bindTo(restTemplate).build();
    client = new GeminiCaptionClient(restTemplate, KEY, MODEL);
  }

  // 모델이 돌려준 JSON 글을 Interactions 응답으로 감쌈
  private static String interaction(String status, String outputText) throws IOException {
    var root = JSON.createObjectNode();
    root.put("id", "v1_test");
    root.put("object", "interaction");
    root.put("model", MODEL);
    root.put("status", status);
    var steps = root.putArray("steps");
    steps.addObject().put("type", "thought");
    var output = steps.addObject();
    output.put("type", "model_output");
    output.putArray("content").addObject().put("type", "text").put("text", outputText);
    return JSON.writeValueAsString(root);
  }

  @Test
  void 키는_헤더로만_보내고_스키마_시스템지시_생각최소를_담아_보냄() throws IOException {
    server.expect(requestTo(GeminiCaptionClient.URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("x-goog-api-key", KEY))
        .andExpect(header("Api-Revision", GeminiCaptionClient.API_REVISION))
        .andExpect(request -> {
          assertThat(request.getURI().toString()).doesNotContain(KEY);
          JsonNode body = JSON.readTree(((MockClientHttpRequest) request).getBodyAsString());
          assertThat(body.path("model").asText()).isEqualTo(MODEL);
          assertThat(body.path("system_instruction").asText()).contains("따르지 않는다");
          assertThat(body.path("input").asText()).contains("가을 음악회");
          assertThat(body.path("response_format").path("mime_type").asText()).isEqualTo("application/json");
          JsonNode props = body.path("response_format").path("schema").path("properties");
          assertThat(props.path("startDate").path("format").asText()).isEqualTo("date");
          assertThat(body.path("generation_config").path("thinking_level").asText()).isEqualTo("minimal");
        })
        .andRespond(withSuccess(interaction("completed", """
            {"isEvent":true,"title":"가을 음악회","category":"공연","startDate":"2026-11-05","endDate":"2026-11-07",
             "timeText":"19:00~21:00","placeName":"가상 아트홀"}
            """), MediaType.APPLICATION_JSON));

    Optional<ParsedEvent> r = client.parse("가을 음악회\n신청 2026. 10. 1. / 공연 2026. 11. 5. ~ 11. 7. 19:00~21:00 가상 아트홀");

    server.verify();
    assertThat(r).contains(new ParsedEvent(true, "가을 음악회", "공연", LocalDate.of(2026, 11, 5),
        LocalDate.of(2026, 11, 7), "19:00~21:00", "가상 아트홀"));
  }

  @Test
  void 보내기_전에_링크_계정_메일_전화번호를_지움() {
    String scrubbed = GeminiCaptionClient.scrub(
        "가을 음악회 @suwon_city 문의 031-228-1234 · culture@suwon.go.kr\n자세히 https://www.suwon.go.kr/a?b=1\n2026. 11. 5.");
    assertThat(scrubbed).doesNotContain("@suwon_city", "031-228-1234", "culture@suwon.go.kr", "https://")
        .contains("가을 음악회", "2026. 11. 5.");
  }

  @Test
  void 행사_아님과_모르는_값() throws IOException {
    server.expect(requestTo(GeminiCaptionClient.URL)).andRespond(withSuccess(interaction("completed", """
        {"isEvent":false,"title":null,"category":null,"startDate":null,"endDate":null,"timeText":null,"placeName":null}
        """), MediaType.APPLICATION_JSON));
    assertThat(client.parse("공지")).get().satisfies(e -> assertThat(e.isEvent()).isFalse());

    server.reset();
    server.expect(requestTo(GeminiCaptionClient.URL)).andRespond(withSuccess(interaction("completed", """
        {"isEvent":null,"title":"가을 음악회","category":null,"startDate":"11월 5일","endDate":null,"timeText":null,"placeName":null}
        """), MediaType.APPLICATION_JSON));
    ParsedEvent e = client.parse("가을 음악회").orElseThrow();
    assertThat(e.isEvent()).isNull();      // 검사기가 FAILED("행사 여부를 모름") 로 처리
    assertThat(e.startDate()).isNull();    // 날짜 모양이 아니면 버림
  }

  @Test
  void 잘린_응답_깨진_JSON_모양이_다른_응답은_못_읽음() throws IOException {
    for (String body : new String[] {
        interaction("incomplete", "{\"isEvent\":true,\"title\":\"가을"),
        interaction("completed", "죄송하지만 읽을 수 없습니다"),
        interaction("completed", "[1,2]"),
        "{\"unexpected\":true}",
        "not json" }) {
      server.reset();
      server.expect(requestTo(GeminiCaptionClient.URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
      assertThat(client.parse("가을 음악회")).as(body).isEmpty();
    }
  }

  @Test
  void 한도_서버오류_연결실패_실패상태는_예외_메시지에_키가_없음() throws IOException {
    for (HttpStatus status : new HttpStatus[] { HttpStatus.TOO_MANY_REQUESTS, HttpStatus.INTERNAL_SERVER_ERROR,
        HttpStatus.FORBIDDEN }) {
      server.reset();
      server.expect(requestTo(GeminiCaptionClient.URL))
          .andRespond(withStatus(status).body("{\"error\":{\"message\":\"key " + KEY + " bad\"}}"));
      assertThatThrownBy(() -> client.parse("가을 음악회"))
          .isInstanceOf(GeminiCaptionClient.GeminiException.class)
          .hasMessageContaining(String.valueOf(status.value()))
          .hasMessageNotContaining(KEY);
    }

    server.reset();
    server.expect(requestTo(GeminiCaptionClient.URL)).andRespond(withException(new IOException("timeout")));
    assertThatThrownBy(() -> client.parse("가을 음악회")).isInstanceOf(GeminiCaptionClient.GeminiException.class);

    server.reset();
    server.expect(requestTo(GeminiCaptionClient.URL))
        .andRespond(withSuccess(interaction("failed", ""), MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> client.parse("가을 음악회")).isInstanceOf(GeminiCaptionClient.GeminiException.class);
  }
}
