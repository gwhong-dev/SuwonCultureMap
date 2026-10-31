package com.suwon.festival.service.place;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

// 장소 이름 → 좌표 (인스타 연동 2단계, 설계 문서 7번 #4 "장소 별칭 · 지역명" 대비)
//
// 원칙: 틀린 위치를 찍느니 안 찍는다
// 1) "행궁동 일대" 처럼 범위가 넓은 말, "A, B" 처럼 장소가 여러 곳이면 검색하지 않고 NO_COORD
// 2) 검색 결과 중 수원 안(주소 "수원시" + 좌표 범위)만 남김
// 3) 1건으로 좁혀질 때만 FOUND. 여러 건이면 이름이 정확히 같은 것이 1건일 때만 FOUND
// 4) 검색 호출 실패는 ERROR (다음 실행에서 다시 시도). 예외 내용(키가 섞일 수 있음)은 남기지 않음
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
  private static final Pattern VAGUE = Pattern.compile("일대|일원|곳곳|전역|인근|주변|미정|추후|온라인|비대면");
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

    List<PlaceCandidate> found;
    try {
      found = client.search(q);
    } catch (RuntimeException e) {
      return new Result(Status.ERROR, null, "장소 검색 실패: " + e.getClass().getSimpleName());
    }
    if (found == null) found = List.of();

    List<PlaceCandidate> inSuwon = found.stream().filter(PlaceGeocoder::inSuwon).toList();
    if (inSuwon.isEmpty()) return noCoord("수원 안 검색 결과 없음");
    if (inSuwon.size() == 1) return new Result(Status.FOUND, inSuwon.get(0), null);

    List<PlaceCandidate> exact = inSuwon.stream().filter(c -> sameName(c.name(), q)).toList();
    if (exact.size() == 1) return new Result(Status.FOUND, exact.get(0), null);
    return noCoord("후보가 여러 개 (" + inSuwon.size() + "건)");
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
