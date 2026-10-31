package com.suwon.festival.service.instagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.suwon.festival.service.instagram.InstagramFetchException.Kind;

// 인스타 수집 호출 모양 — 가짜 서버로 확인 (실제 Meta 호출 없음, 캡션은 직접 만든 가상 문장)
class InstagramClientTest {

  private static final String TOKEN = "test-ig-token";
  private static final String USER_ID = "17840000000000000";

  private RestTemplate restTemplate;
  private MockRestServiceServer server;
  private InstagramClient client;

  @BeforeEach
  void setUp() {
    restTemplate = new RestTemplate();
    server = MockRestServiceServer.bindTo(restTemplate).build();
    client = new InstagramClient(restTemplate, TOKEN, USER_ID, "v25.0");
  }

  @Test
  void 토큰은_헤더로_fields는_인코딩해서_보내고_게시물을_읽음() {
    server.expect(requestTo(Matchers.startsWith("https://graph.facebook.com/v25.0/" + USER_ID + "?")))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Authorization", "Bearer " + TOKEN))
        .andExpect(request -> {
          var params = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
          String fields = URLDecoder.decode(params.getFirst("fields"), StandardCharsets.UTF_8);
          assertThat(fields).isEqualTo(
              "business_discovery.username(gasang_city){media.limit(5){id,caption,permalink,timestamp,media_type}}");
          assertThat(request.getURI().toString()).doesNotContain(TOKEN); // 토큰은 주소에 넣지 않음
        })
        .andRespond(withSuccess("""
            {"business_discovery":{"media":{"data":[
              {"id":"1","caption":"가상 행사 안내","permalink":"https://www.instagram.com/p/AAA/",
               "timestamp":"2026-10-30T09:00:00+0000","media_type":"IMAGE"},
              {"id":"2","permalink":"https://www.instagram.com/p/BBB/","timestamp":"이상한값","media_type":"VIDEO"},
              {"caption":"id 없음"}
            ]},"id":"999"}}
            """, MediaType.APPLICATION_JSON));

    List<InstagramPost> posts = client.fetchRecent("gasang_city", 5);

    server.verify();
    assertThat(posts).hasSize(2); // id 없는 항목은 버림
    assertThat(posts.get(0).caption()).isEqualTo("가상 행사 안내");
    assertThat(posts.get(0).timestamp()).isEqualTo(OffsetDateTime.of(2026, 10, 30, 9, 0, 0, 0, ZoneOffset.UTC));
    assertThat(posts.get(1).caption()).isNull(); // 캡션 없는 게시물
    assertThat(posts.get(1).timestamp()).isNull(); // 시간 모양이 이상하면 비움
  }

  @Test
  void 게시물이_없으면_빈_목록() {
    server.expect(requestTo(Matchers.startsWith("https://graph.facebook.com/")))
        .andRespond(withSuccess("{\"business_discovery\":{\"id\":\"1\"}}", MediaType.APPLICATION_JSON));
    assertThat(client.fetchRecent("gasang_city", 5)).isEmpty();
  }

  @Test
  void business_discovery_가_없으면_BAD_RESPONSE() {
    server.expect(requestTo(Matchers.startsWith("https://graph.facebook.com/")))
        .andRespond(withSuccess("{\"id\":\"1\"}", MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> client.fetchRecent("gasang_city", 5))
        .isInstanceOfSatisfying(InstagramFetchException.class, e -> assertThat(e.kind()).isEqualTo(Kind.BAD_RESPONSE));
  }

  @Test
  void 토큰_만료_190은_TOKEN_INVALID_메시지에_토큰_없음() {
    server.expect(requestTo(Matchers.startsWith("https://graph.facebook.com/")))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
            .body("{\"error\":{\"message\":\"Error validating access token\",\"type\":\"OAuthException\",\"code\":190,\"error_subcode\":463}}"));
    assertThatThrownBy(() -> client.fetchRecent("gasang_city", 5))
        .isInstanceOfSatisfying(InstagramFetchException.class, e -> {
          assertThat(e.kind()).isEqualTo(Kind.TOKEN_INVALID);
          assertThat(e.code()).isEqualTo(190);
          assertThat(e.getMessage()).doesNotContain(TOKEN).doesNotContain("validating");
        });
  }

  @Test
  void 오류_본문이_JSON이_아니어도_OTHER() {
    server.expect(requestTo(Matchers.startsWith("https://graph.facebook.com/")))
        .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("<html>bad</html>"));
    assertThatThrownBy(() -> client.fetchRecent("gasang_city", 5))
        .isInstanceOfSatisfying(InstagramFetchException.class, e -> assertThat(e.kind()).isEqualTo(Kind.OTHER));
  }

  @Test
  void 오류_번호별_종류() {
    assertThat(InstagramClient.kindOf(100)).isEqualTo(Kind.NOT_FOUND);
    assertThat(InstagramClient.kindOf(4)).isEqualTo(Kind.RATE_LIMIT);
    assertThat(InstagramClient.kindOf(613)).isEqualTo(Kind.RATE_LIMIT);
    assertThat(InstagramClient.kindOf(10)).isEqualTo(Kind.PERMISSION);
    assertThat(InstagramClient.kindOf(250)).isEqualTo(Kind.PERMISSION);
    assertThat(InstagramClient.kindOf(null)).isEqualTo(Kind.OTHER);
  }

  @Test
  void 계정_이름_형식이_틀리면_호출하지_않음() {
    // fields 문자열을 깨뜨릴 수 있는 글자 · 너무 큰 개수
    for (String bad : List.of("a){media", "가상계정", "", "a".repeat(31))) {
      assertThatThrownBy(() -> client.fetchRecent(bad, 5))
          .isInstanceOfSatisfying(InstagramFetchException.class, e -> assertThat(e.kind()).isEqualTo(Kind.INVALID_INPUT));
    }
    assertThatThrownBy(() -> client.fetchRecent("gasang_city", 0)).isInstanceOf(InstagramFetchException.class);
    assertThatThrownBy(() -> client.fetchRecent("gasang_city", 51)).isInstanceOf(InstagramFetchException.class);
    server.verify(); // 요청이 한 번도 안 나감
  }

  @Test
  void 설정이_비어_있으면_NOT_CONFIGURED() {
    InstagramClient empty = new InstagramClient(restTemplate, "", "", "v25.0");
    assertThat(empty.isConfigured()).isFalse();
    assertThatThrownBy(() -> empty.fetchRecent("gasang_city", 5))
        .isInstanceOfSatisfying(InstagramFetchException.class, e -> assertThat(e.kind()).isEqualTo(Kind.NOT_CONFIGURED));
    // 버전 · ID 모양이 틀려도 설정 안 된 것으로 봄
    assertThat(new InstagramClient(restTemplate, TOKEN, "abc", "v25.0").isConfigured()).isFalse();
    assertThat(new InstagramClient(restTemplate, TOKEN, USER_ID, "25").isConfigured()).isFalse();
  }

  @Test
  void 시간_모양_두_가지_모두_읽음() {
    assertThat(InstagramClient.parseTime("2026-10-30T09:00:00+0000")).isNotNull();
    assertThat(InstagramClient.parseTime("2026-10-30T09:00:00+00:00")).isNotNull();
    assertThat(InstagramClient.parseTime(null)).isNull();
  }
}
