package com.suwon.festival.service.swdb;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

// 수원문화DB 행사정보 목록 · 상세 요청 (robots.txt 2026-11-13 확인)
//  - 쓰는 주소: 목록 ?p=15&page=N, 상세 ?p=15_view&reqIdx=..&page=1 만. 검색 주소(searchTxt · mode=search)는 쓰지 않음 (robots.txt 금지)
//  - 요청 사이 1.5초 쉼, User-Agent 에 서비스 이름 · 주소 표기
@Component
public class HttpSwdbClient implements SwdbClient {

  static final String BASE = "https://www.swcf.or.kr/swdb/";
  static final String USER_AGENT = "SuwonCultureMap/1.0 (+https://suwonbible.com)";
  static final long PAUSE_MS = 1500;
  private static final Pattern REQ_IDX = Pattern.compile("\\d{14,20}");

  // 쉼 (시험에서는 바로 넘어가게 바꿈)
  interface Pause {
    void pause() throws InterruptedException;
  }

  private final RestTemplate restTemplate;
  private final Pause pause;
  private boolean first = true;

  @Autowired
  public HttpSwdbClient(RestTemplate restTemplate) {
    this(restTemplate, () -> Thread.sleep(PAUSE_MS));
  }

  HttpSwdbClient(RestTemplate restTemplate, Pause pause) {
    this.restTemplate = restTemplate;
    this.pause = pause;
  }

  @Override
  public String listPage(int page) {
    if (page < 1) throw new IllegalArgumentException("page");
    return get(UriComponentsBuilder.fromUriString(BASE).queryParam("p", "15").queryParam("page", page)
        .build().toUri());
  }

  @Override
  public String detailPage(String reqIdx) {
    if (reqIdx == null || !REQ_IDX.matcher(reqIdx).matches()) throw new IllegalArgumentException("reqIdx");
    return get(UriComponentsBuilder.fromUriString(BASE).queryParam("p", "15_view").queryParam("reqIdx", reqIdx)
        .queryParam("page", 1).build().toUri());
  }

  public static String detailUrl(String reqIdx) {
    return BASE + "?p=15_view&reqIdx=" + reqIdx + "&page=1";
  }

  private synchronized String get(URI uri) {
    if (!first) {
      try {
        pause.pause();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("수원문화DB 요청 쉼이 끊김", e);
      }
    }
    first = false;
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
    byte[] body = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), byte[].class).getBody();
    return body == null ? "" : new String(body, StandardCharsets.UTF_8);
  }
}
