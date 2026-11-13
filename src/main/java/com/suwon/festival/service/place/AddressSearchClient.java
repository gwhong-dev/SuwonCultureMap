package com.suwon.festival.service.place;

import java.util.List;

// 주소(도로명 · 지번) → 좌표 후보. 실제 구현은 카카오 주소 검색, 시험은 가짜
// 네트워크 · 응답 오류는 RuntimeException 으로 던짐
public interface AddressSearchClient {

  List<PlaceCandidate> search(String address);
}
