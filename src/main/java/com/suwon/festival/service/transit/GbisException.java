package com.suwon.festival.service.transit;

// 경기도 정류소 API 호출 실패
// - 메시지에 요청 주소를 넣지 않음: 주소에 서비스키(serviceKey)가 들어 있어 로그에 남으면 키가 새어 나감
// - 같은 이유로 원래 예외(cause)를 붙이지 않음 (RestTemplate 예외 메시지에 주소가 들어감)
public class GbisException extends RuntimeException {

  public GbisException(String message) {
    super(message);
  }
}
