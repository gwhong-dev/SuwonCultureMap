package com.suwon.festival.dto.instagram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Graph API 오류 응답: { "error": { "code": 190, "error_subcode": 463, "message": "..." } }
@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphErrorResponse {
  public Error error;

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Error {
    public Integer code;
    public Integer error_subcode;
  }
}
