package com.suwon.festival.service.place;

// 좌표 → 시도 · 시군구 이름. 실제 구현은 카카오, 테스트는 가짜
// 네트워크 · 응답 오류는 RuntimeException 으로 던짐 (EventDistrictFiller 가 "실패"로 세고 다음 동기화 때 다시)
public interface RegionLookupClient {

  // 행정구역을 못 찾으면(바다 등) 두 칸 모두 null
  record Region(String sido, String sigungu) {

    public static final Region NONE = new Region(null, null);
  }

  Region lookup(double latitude, double longitude);
}
