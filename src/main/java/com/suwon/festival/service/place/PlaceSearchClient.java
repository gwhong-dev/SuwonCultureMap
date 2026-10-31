package com.suwon.festival.service.place;

import java.util.List;

// 장소 이름 → 검색 결과 목록. 실제 구현은 카카오, 테스트는 가짜
// 네트워크 · 응답 오류는 RuntimeException 으로 던짐 (PlaceGeocoder 가 ERROR 로 바꿈)
public interface PlaceSearchClient {

  List<PlaceCandidate> search(String query);
}
