package com.suwon.festival.service.swdb;

// 수원문화DB 화면 받기. 실제 구현은 HttpSwdbClient(요청 사이 쉼), 시험은 가짜
// 네트워크 · 응답 오류는 RuntimeException 으로 던짐
public interface SwdbClient {

  String listPage(int page);

  String detailPage(String reqIdx);
}
