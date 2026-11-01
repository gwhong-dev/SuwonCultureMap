package com.suwon.festival.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.suwon.festival.dto.kakao.KakaoTokenResponse;
import com.suwon.festival.dto.kakao.KakaoUserResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KakaoAuthService {

  private static final Logger log = LoggerFactory.getLogger(KakaoAuthService.class);

  private final RestTemplate restTemplate;

  @Value("${kakao.rest-api-key}")
  private String restApiKey;

  @Value("${kakao.redirect-uri}")
  private String redirectUri;

  @Value("${kakao.client-secret}")
  private String clientSecret;

  // 탈퇴 때 카카오 연결 해제에 씀. 비어 있으면 해제를 못 하고 경고만 남김
  @Value("${kakao.admin-key:}")
  private String adminKey;

  public String getAccessToken(String code) {
    String url = "https://kauth.kakao.com/oauth/token";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("grant_type", "authorization_code");
    params.add("client_id", restApiKey);
    params.add("redirect_uri", redirectUri);
    params.add("code", code);
    params.add("client_secret", clientSecret);

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

    KakaoTokenResponse response = restTemplate.postForObject(url, request, KakaoTokenResponse.class);
    return response.access_token;
  }

  public KakaoUserResponse getUserInfo(String accessToken) {
    String url = "https://kapi.kakao.com/v2/user/me";

    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + accessToken);

    HttpEntity<Void> request = new HttpEntity<>(headers);

    return restTemplate.exchange(url, HttpMethod.GET, request, KakaoUserResponse.class).getBody();
  }

  // 연결 해제: 카카오 문서상 회원 탈퇴 과정에 반드시 포함. 토큰을 저장하지 않으므로 어드민 키 + 회원번호 방식
  // 실패해도 예외를 던지지 않음: 우리 DB 는 이미 지워졌음 → 결과만 돌려주고 사용자에게 직접 해제를 안내
  public boolean unlink(String kakaoUserId) {
    if (adminKey == null || adminKey.isBlank()) {
      log.warn("카카오 어드민 키가 없어 연결 해제를 건너뜀 (kakao.admin-key)");
      return false;
    }
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    headers.set("Authorization", "KakaoAK " + adminKey);

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("target_id_type", "user_id");
    params.add("target_id", kakaoUserId);

    try {
      restTemplate.postForEntity("https://kapi.kakao.com/v1/user/unlink", new HttpEntity<>(params, headers), String.class);
      return true;
    } catch (RestClientException e) {
      // 메시지 · 회원번호는 남기지 않음 → 예외 종류만
      log.warn("카카오 연결 해제 실패 {}", e.getClass().getSimpleName());
      return false;
    }
  }
}