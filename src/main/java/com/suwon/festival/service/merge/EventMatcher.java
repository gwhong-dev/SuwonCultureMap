package com.suwon.festival.service.merge;

import java.time.LocalDate;

// "같은 행사인가" 규칙 (설계 docs/event-merge.md 3번). ① 날짜 ② 장소 ③ 제목 3가지 모두 맞아야 같음
// 원칙: 틀리게 합치기가 안 합치기보다 손해 → 애매하면 false
// 숫자(300m · 0.6 · 4글자)는 3번 PR 미리보기 · 실사용 피드백으로 조정 (설계 8번)
public final class EventMatcher {

  public static final double MAX_DISTANCE_METERS = 300.0;
  public static final double MIN_TITLE_SIMILARITY = 0.6;
  public static final int MIN_CONTAINED_LENGTH = 4;

  private static final double EARTH_RADIUS_METERS = 6_371_000.0;

  private EventMatcher() {
  }

  public static boolean isSameEvent(MergeCandidate a, MergeCandidate b) {
    return datesOverlap(a, b) && samePlace(a, b) && similarTitle(a.title(), b.title());
  }

  // ① 기간이 겹치는가. 종료일이 없으면 시작일 하루, 시작일이 없으면 ❌
  static boolean datesOverlap(MergeCandidate a, MergeCandidate b) {
    if (a.startDate() == null || b.startDate() == null) {
      return false;
    }
    LocalDate aEnd = endOf(a);
    LocalDate bEnd = endOf(b);
    return !a.startDate().isAfter(bEnd) && !b.startDate().isAfter(aEnd);
  }

  // 종료일이 없거나 시작일보다 앞이면(잘못 들어온 값) 시작일 하루로 봄
  private static LocalDate endOf(MergeCandidate c) {
    LocalDate end = c.endDate();
    return end == null || end.isBefore(c.startDate()) ? c.startDate() : end;
  }

  // ② 좌표 300m 이내 또는 정리한 장소명이 같음
  static boolean samePlace(MergeCandidate a, MergeCandidate b) {
    if (a.latitude() != null && a.longitude() != null && b.latitude() != null && b.longitude() != null
        && distanceMeters(a.latitude(), a.longitude(), b.latitude(), b.longitude()) <= MAX_DISTANCE_METERS) {
      return true;
    }
    String pa = MergeText.normalize(a.place());
    return !pa.isEmpty() && pa.equals(MergeText.normalize(b.place()));
  }

  // ③ 정리한 제목이 같음 · 한쪽이 다른 쪽을 포함(짧은 쪽 4글자 이상) · 글자쌍 유사도 0.6 이상
  static boolean similarTitle(String titleA, String titleB) {
    String a = MergeText.normalize(titleA);
    String b = MergeText.normalize(titleB);
    if (a.isEmpty() || b.isEmpty()) {
      return false;
    }
    if (a.equals(b)) {
      return true;
    }
    String shorter = a.length() <= b.length() ? a : b;
    String longer = shorter == a ? b : a;
    // 숫자뿐이면(`2026`) 어느 제목에나 들어 있어 포함으로 치지 않음
    if (shorter.length() >= MIN_CONTAINED_LENGTH && longer.contains(shorter)
        && shorter.chars().anyMatch(Character::isLetter)) {
      return true;
    }
    return MergeText.dice(a, b) >= MIN_TITLE_SIMILARITY && !differsAfterCommonPrefix(a, b);
  }

  // 시설명 · 연도가 앞에 붙으면 전체 유사도가 부풀려짐 → 같은 앞부분을 뺀 나머지도 비슷해야 함
  // `정조테마공연장2026작은음악회` ↔ `정조테마공연장2026마당놀이터` (전체 0.67, 나머지 0)
  // 나머지가 1글자 이하면 오타 · 회차 번호 수준이라 전체 유사도만 봄
  private static boolean differsAfterCommonPrefix(String a, String b) {
    int common = 0;
    while (common < a.length() && common < b.length() && a.charAt(common) == b.charAt(common)) {
      common++;
    }
    String restA = a.substring(common);
    String restB = b.substring(common);
    return restA.length() >= 2 && restB.length() >= 2 && MergeText.dice(restA, restB) < MIN_TITLE_SIMILARITY;
  }

  // inner 의 기간이 outer 기간 안에 있는가
  static boolean periodWithin(MergeCandidate inner, MergeCandidate outer) {
    return !inner.startDate().isBefore(outer.startDate()) && !endOf(inner).isAfter(endOf(outer));
  }

  // 두 좌표 사이 거리(m) — 하버사인 공식 (지구를 구로 보고 계산, 수백 m 범위에선 오차 무시 가능)
  static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
    double dLat = Math.toRadians(lat2 - lat1);
    double dLng = Math.toRadians(lng2 - lng1);
    double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(h)));
  }
}
