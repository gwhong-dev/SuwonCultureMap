package com.suwon.festival.dto.kakao;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 카카오 로컬 "좌표로 행정구역정보 받기" 응답 (필요한 칸만)
// region_type: H = 행정동, B = 법정동. 일반구가 있는 시는 region_2depth_name 에 "수원시 팔달구"처럼 시 · 구가 함께 옴
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoRegionResponse {
  public List<Document> documents;

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Document {
    public String region_type;
    public String region_1depth_name; // 시도 (예: 경기도)
    public String region_2depth_name; // 시군구 (예: 수원시 팔달구). 바다면 빈 값
  }
}
