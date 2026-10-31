package com.suwon.festival.service.place;

import java.util.List;

// 장소 이름 → 검색 결과 목록. 실제 구현은 카카오, 테스트는 가짜
// 네트워크 · 응답 오류는 RuntimeException 으로 던짐 (PlaceGeocoder 가 ERROR 로 바꿈)
public interface PlaceSearchClient {

  // complete: 받은 목록이 결과 전부인지 (false 면 다음 페이지에 더 있음 → "1건으로 좁혀짐"이라 볼 수 없음)
  record Page(List<PlaceCandidate> places, boolean complete) {

    public Page {
      places = places == null ? List.of() : List.copyOf(places);
    }
  }

  Page search(String query);
}
