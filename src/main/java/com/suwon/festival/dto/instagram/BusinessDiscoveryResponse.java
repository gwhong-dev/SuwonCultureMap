package com.suwon.festival.dto.instagram;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Instagram Graph API Business Discovery 응답 (필요한 칸만)
// { "business_discovery": { "media": { "data": [ {id, caption, permalink, timestamp, media_type} ] } }, "id": "..." }
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
  }
}
