package com.suwon.festival.service.place;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

// 장소 이름 → 좌표 (인스타 연동 2단계, 설계 문서 7번 #4 "장소 별칭 · 지역명" 대비)
//
// 원칙: 틀린 위치를 찍느니 안 찍는다
// 1) "행궁동 일대" 처럼 범위가 넓은 말, "A, B" 처럼 장소가 여러 곳이면 검색하지 않고 NO_COORD
// 2) 검색 결과 중 수원 안(주소 "수원시" + 좌표 범위)만 남김
// 3) 이름 확인: 결과 이름 = 검색어(공백 무시), 또는 검색어가 결과 이름을 품을 때("가상 아트홀 2층" ⊇ "가상아트홀")만 인정
//    결과 이름이 검색어를 품는 경우("행궁동" → "행궁동 공방거리")는 다른 곳일 수 있어 인정하지 않음
// 4) 이름이 맞는 수원 결과가 1건이고, 결과가 다음 페이지에 더 없을 때만 FOUND
//    (다음 페이지가 있으면 이름이 정확히 같은 1건일 때만)
// 5) "행궁동" "팔달구" 처럼 동 · 구 · 시 이름뿐인 검색어는 지역명 → 검색 안 함
// 6) 검색 호출 실패는 ERROR (다음 실행에서 다시 시도). 예외 내용(키가 섞일 수 있음)은 남기지 않고 HTTP 상태 번호만
@Service
public class PlaceGeocoder {

  public enum Status { FOUND, NO_COORD, ERROR }

  public record Result(Status status, PlaceCandidate place, String reason) {
  }

  static final int QUERY_MAX = 100;

  // 수원시 좌표 범위 (넉넉하게). 주소 검사와 함께 씀
  static final double LAT_MIN = 37.20, LAT_MAX = 37.36;
  static final double LNG_MIN = 126.90, LNG_MAX = 127.12;

  // 범위가 넓은 말 · 정해지지 않은 장소
  private static final Pattern VAGUE = Pattern.compile("일대|일원|곳곳|전역|인근|주변|미정|추후|온라인|비대면|시내|관내|각\\s|\\s등$");
  // 지역명뿐: "행궁동" "팔달구" "수원시" "광교1동" (공백 없앤 뒤 6글자 이하 + 동 · 구 · 시 · 읍 · 면 · 리 · 가)
  private static final Pattern REGION_ONLY = Pattern.compile("^[가-힣0-9]{1,5}(동|구|시|읍|면|리|가)$");
  // 결과 이름이 이보다 짧으면 "검색어가 결과 이름을 품음"으로 인정하지 않음 ("공원" 같은 흔한 말 방지)
  static final int CONTAINED_NAME_MIN = 3;
  // 장소 여러 곳: "A, B" / "A · B" / "A / B" / "A 및 B"
  private static final Pattern MULTI = Pattern.compile("[,，·/]|\\s및\\s");

  private final PlaceSearchClient client;

  public PlaceGeocoder(PlaceSearchClient client) {
    this.client = client;
  }

  public Result geocode(String placeName) {
    String q = placeName == null ? "" : placeName.strip().replaceAll("\\s+", " ");
    if (q.length() < 2) return noCoord("장소 없음");
    if (VAGUE.matcher(q).find()) return noCoord("범위가 넓은 장소");
    if (MULTI.matcher(q).find()) return noCoord("장소가 여러 곳");
    if (q.length() > QUERY_MAX) return noCoord("장소 이름이 너무 김");
    if (REGION_ONLY.matcher(squash(q)).matches()) return noCoord("지역 이름뿐");

    PlaceSearchClient.Page page;
    try {
      page = client.search(q);
    } catch (RuntimeException e) {
      String status = e instanceof RestClientResponseException r ? " (HTTP " + r.getStatusCode().value() + ")" : "";
      return new Result(Status.ERROR, null, "장소 검색 실패: " + e.getClass().getSimpleName() + status);
    }
    if (page == null) page = new PlaceSearchClient.Page(List.of(), true);

    List<PlaceCandidate> inSuwon = page.places().stream().filter(PlaceGeocoder::inSuwon).toList();
    if (inSuwon.isEmpty()) return noCoord("수원 안 검색 결과 없음");

    List<PlaceCandidate> exact = inSuwon.stream().filter(c -> sameName(c.name(), q)).toList();
    if (exact.size() == 1) return new Result(Status.FOUND, exact.get(0), null);
    if (exact.size() > 1) return noCoord("이름이 같은 곳이 여러 개 (" + exact.size() + "건)");
    if (!page.complete()) return noCoord("결과가 더 있어 1건으로 못 좁힘");

    List<PlaceCandidate> named = inSuwon.stream().filter(c -> queryContainsName(q, c.name())).toList();
    if (named.size() == 1) return new Result(Status.FOUND, named.get(0), null);
    if (named.isEmpty()) return noCoord("이름이 맞는 결과 없음");
    return noCoord("후보가 여러 개 (" + named.size() + "건)");
  }

  // "가상 아트홀 2층" ⊇ "가상아트홀" (결과 이름이 너무 짧으면 불인정)
  static boolean queryContainsName(String query, String name) {
    if (name == null) return false;
    String n = squash(name);
    return n.length() >= CONTAINED_NAME_MIN && squash(query).contains(n);
  }

  static boolean inSuwon(PlaceCandidate c) {
    boolean address = hasSuwon(c.address()) || hasSuwon(c.roadAddress());
    boolean box = c.lat() >= LAT_MIN && c.lat() <= LAT_MAX && c.lng() >= LNG_MIN && c.lng() <= LNG_MAX;
    return address && box;
  }

  // 카카오 주소 모양: "경기 수원시 팔달구 …" (도로명은 "경기 수원시 …")
  private static boolean hasSuwon(String address) {
    return address != null && address.matches("^(경기|경기도)\\s+수원시(\\s.*)?$");
  }

  private static boolean sameName(String a, String b) {
    if (a == null) return false;
    return squash(a).equals(squash(b));
  }

  private static String squash(String s) {
    return s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
  }

  private static Result noCoord(String reason) {
    return new Result(Status.NO_COORD, null, reason);
  }
}
