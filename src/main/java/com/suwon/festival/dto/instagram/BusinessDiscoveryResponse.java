package com.suwon.festival.dto.instagram;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Instagram Graph API Business Discovery 응답 (필요한 칸만)
// { "business_discovery": { "media": { "data": [ {id, caption, permalink, timestamp, media_type, media_url, thumbnail_url} ] } }, "id": "..." }
@JsonIgnoreProperties(ignoreUnknown = true)
public class BusinessDiscoveryResponse {
  public BusinessDiscovery business_discovery;

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class BusinessDiscovery {
    public Media media;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Media {
    public List<Item> data;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Item {
    public String id;
    public String caption;
    public String permalink;
    public String timestamp; // 예: 2026-10-30T09:00:00+0000 (UTC)
    public String media_type;
    public String media_url;     // 사진 · 묶음(첫 장) 주소. 만료되는 주소라 받아서 저장 (InstagramImageStore)
    public String thumbnail_url; // 동영상 표지 사진 주소
  }
}
