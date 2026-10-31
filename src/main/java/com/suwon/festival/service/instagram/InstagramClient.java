package com.suwon.festival.service.instagram;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.suwon.festival.dto.instagram.BusinessDiscoveryResponse;
import com.suwon.festival.dto.instagram.GraphErrorResponse;
import com.suwon.festival.service.instagram.InstagramFetchException.Kind;

// 인스타 4단계: 다른 프로페셔널 계정의 최근 게시물을 공식 API(Business Discovery)로 가져옴
// - 호출 모양은 0단계 · 캡션 확인 스크립트(scripts/instagram-peek.ps1)에서 실제로 확인한 것과 같음
//   GET https://graph.facebook.com/{버전}/{내 인스타 계정 ID}?fields=business_discovery.username({계정}){media.limit(N){...}}
// - 토큰은 Authorization 헤더로만 보냄 (주소 · 로그 · 예외 메시지에 남지 않게)
// - 토큰이나 내 계정 ID 설정이 비어 있으면 호출하지 않음 (isConfigured)
@Component
public class InstagramClient {

  static final String BASE_URL = "https://graph.facebook.com";
  static final String MEDIA_FIELDS = "id,caption,permalink,timestamp,media_type";
  static final int LIMIT_MAX = 25; // 캡션 확인 스크립트와 같은 상한 (실제로 확인한 건 5개)

  // 인스타 계정 이름 규칙: 영문 · 숫자 · 마침표 · 밑줄, 30자 이하 (fields 문자열에 그대로 들어가므로 엄격히 검사)
  private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._]{1,30}$");
  private static final Pattern USER_ID = Pattern.compile("^[0-9]{5,30}$");
  private static final Pattern VERSION = Pattern.compile("^v\\d+\\.\\d+$");
  // 토큰 글자 (instagram-peek.ps1 과 같은 규칙). 줄바꿈 등이 섞이면 HTTP 라이브러리가 토큰을 예외 메시지에 넣어 버림
  private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_\\-.|]+$");
  // Graph 의 시간 모양: 2026-10-30T09:00:00+0000 (콜론 없는 시간대)
  private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

  private final RestTemplate restTemplate;
  private final String accessToken;
  private final String userId;
  private final String graphVersion;

  public InstagramClient(RestTemplate restTemplate,
      @Value("${instagram.access-token:}") String accessToken,
      @Value("${instagram.user-id:}") String userId,
      @Value("${instagram.graph-version:v25.0}") String graphVersion) {
    this.restTemplate = restTemplate;
    this.accessToken = accessToken == null ? "" : accessToken.strip();
    this.userId = userId == null ? "" : userId.strip();
    this.graphVersion = graphVersion == null ? "" : graphVersion.strip();
  }

  // 토큰 · 내 계정 ID · 버전이 모두 올바른 모양일 때만 true
  public boolean isConfigured() {
    return TOKEN.matcher(accessToken).matches() && USER_ID.matcher(userId).matches()
        && VERSION.matcher(graphVersion).matches();
  }

  public List<InstagramPost> fetchRecent(String username, int limit) {
    if (!isConfigured()) throw new InstagramFetchException(Kind.NOT_CONFIGURED, null);
    if (username == null || !USERNAME.matcher(username).matches() || limit < 1 || limit > LIMIT_MAX) {
      throw new InstagramFetchException(Kind.INVALID_INPUT, null);
    }

    String fields = "business_discovery.username(" + username + "){media.limit(" + limit + "){" + MEDIA_FIELDS + "}}";
    // fields 에 { } ( ) 가 들어가므로 URI 변수로 넣고 엄격 인코딩
    URI uri = UriComponentsBuilder.fromUriString(BASE_URL)
        .path("/{version}/{userId}")
        .queryParam("fields", "{fields}")
        .encode()
        .buildAndExpand(graphVersion, userId, fields)
        .toUri();

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);

    BusinessDiscoveryResponse body;
    try {
      body = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), BusinessDiscoveryResponse.class)
          .getBody();
    } catch (RestClientResponseException e) {
      Integer code = graphCode(e);
      throw new InstagramFetchException(kindOf(code), code);
    } catch (ResourceAccessException e) {
      throw new InstagramFetchException(Kind.OTHER, null); // 연결 실패 · 시간 초과
    } catch (RestClientException e) {
      throw new InstagramFetchException(Kind.BAD_RESPONSE, null); // 200 인데 본문을 못 읽음
    }

    if (body == null || body.business_discovery == null) throw new InstagramFetchException(Kind.BAD_RESPONSE, null);
    List<InstagramPost> posts = new ArrayList<>();
    if (body.business_discovery.media == null || body.business_discovery.media.data == null) return posts;
    for (BusinessDiscoveryResponse.Item item : body.business_discovery.media.data) {
      if (item == null || item.id == null || item.id.isBlank()) continue; // id 없는 건 중복 판단을 못 하니 버림
      posts.add(new InstagramPost(item.id, item.caption, item.permalink, parseTime(item.timestamp), item.media_type));
    }
    return posts;
  }

  static Kind kindOf(Integer code) {
    if (code == null) return Kind.OTHER;
    if (code == 190 || code == 102) return Kind.TOKEN_INVALID;
    // 100 은 "잘못된 요청" 전반이지만, Business Discovery 에선 계정 이름 · 내 계정 ID 가 틀릴 때 주로 나옴 (0단계 실제 경험)
    if (code == 100 || code == 110) return Kind.NOT_FOUND;
    if (code == 4 || code == 17 || code == 32 || code == 341 || code == 613 || code == 80002) return Kind.RATE_LIMIT;
    if (code == 3 || code == 10 || (code >= 200 && code <= 299)) return Kind.PERMISSION;
    return Kind.OTHER;
  }

  // 오류 본문에서 Graph 오류 번호만 꺼냄 (메시지 원문은 버림)
  private static final Pattern ERROR_CODE = Pattern.compile("\"code\"\\s*:\\s*(\\d{1,6})");

  private static Integer graphCode(RestClientResponseException e) {
    try {
      GraphErrorResponse err = e.getResponseBodyAs(GraphErrorResponse.class);
      if (err != null && err.error != null && err.error.code != null) return err.error.code;
    } catch (RuntimeException ignore) {
      // 변환기가 없거나 본문이 JSON 이 아님 → 아래 글자 검색으로
    }
    var m = ERROR_CODE.matcher(e.getResponseBodyAsString());
    return m.find() ? Integer.valueOf(m.group(1)) : null;
  }

  static OffsetDateTime parseTime(String value) {
    if (value == null) return null;
    try {
      return OffsetDateTime.parse(value, TIMESTAMP);
    } catch (DateTimeParseException e) {
      try {
        return OffsetDateTime.parse(value); // 콜론 있는 ISO 모양도 허용
      } catch (DateTimeParseException e2) {
        return null;
      }
    }
  }
}
