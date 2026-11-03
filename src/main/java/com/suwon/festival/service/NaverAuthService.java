package com.suwon.festival.service;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.suwon.festival.dto.naver.NaverAccount;
import com.suwon.festival.dto.naver.NaverTokenResponse;
import com.suwon.festival.dto.naver.NaverUserResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NaverAuthService {

    private final RestTemplate restTemplate;

    @Value("${naver.client-id}")
    private String clientId;

    @Value("${naver.client-secret}")
    private String clientSecret;

    public String getAccessToken(String code, String state) {
        // code · state 는 네이버가 준 값이라 + & = 같은 글자가 섞일 수 있음
        // → URI 변수로 넣고 엄격 인코딩 (+ 가 공백으로 바뀌거나 & 에서 값이 잘리지 않게)
        URI uri = UriComponentsBuilder.fromUriString("https://nid.naver.com/oauth2.0/token")
                .queryParam("grant_type", "authorization_code")
                .queryParam("client_id", "{clientId}")
                .queryParam("client_secret", "{clientSecret}")
                .queryParam("code", "{code}")
                .queryParam("state", "{state}")
                .encode()
                .buildAndExpand(clientId, clientSecret, code, state)
                .toUri();

        NaverTokenResponse response = restTemplate.getForObject(uri, NaverTokenResponse.class);
        return response.access_token;
    }

    public NaverAccount getUserInfo(String accessToken) {
        String url = "https://openapi.naver.com/v1/nid/me";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        NaverUserResponse response = restTemplate.exchange(url, HttpMethod.GET, request, NaverUserResponse.class)
                .getBody();
        return response.response;
    }
}