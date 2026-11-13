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
// 7) 검색 전 다듬기: 괄호 부연 "(1F)" "(1층 전시홀, 3층 컨벤션홀)" 과 앞 라벨 "집결지:" 를 지움 (괄호 속 쉼표로 "여러 곳" 오판 방지)
// 8) 못 찾으면 뒤 단어를 하나씩 떼고 다시 검색 (최대 2번): "수원시청 본관 로비" → "수원시청 본관" → "수원시청"
//    - 떼는 단어는 방 · 층 모양(숫자 포함, "로비" "홀" "강당" "극장" "체육관" …)일 때만 → "수원화성 장안문" 처럼 독립된 장소는 안 뗌
//    - 줄인 검색어는 결과 이름과 "정확히 같을 때만" 인정
//    - 3글자 미만 · 지역 이름뿐 · 생활권 이름(서수원 · 영통 …) · 범위가 넓은 말이면 멈춤
//    → 방 · 층 대신 건물 좌표에 찍힘
// 9) "·" "/" (와 ㆍ ・ • ／ 같은 비슷한 모양, DOT_SLASH_SEPS) 는 기본 "여러 곳"(실측 "영흥수목원·영흥숲공원"). 단, 뒤 조각이 모두 방 · 층 이름뿐이면
//    한 장소의 다른 표기("가상아트센터 · 대극장" "가상미술관 1층/2층") → 앞 조각(건물)만 검색
//    - 앞 조각이 방 · 층 이름이면("1층 · 2층") 여전히 여러 곳
//    - 뒤 조각이 장소가 될 수 있는 말(광장 · 마당 · 주차장 · 공원 · 미술관 …)이면 여러 곳 (틀린 좌표보다 안전)
//    - 쉼표 · "및" 은 그대로 여러 곳
@Service
public class PlaceGeocoder {

  public enum Status { FOUND, NO_COORD, ERROR }

  public record Result(Status status, PlaceCandidate place, String reason) {
  }

  static final int QUERY_MAX = 100;
  static final int SHORTEN_MAX = 2; // 뒤 단어를 떼고 다시 검색하는 최대 횟수 (카카오 호출 수 제한)
  static final int SHORTENED_MIN = 3; // 줄인 검색어 최소 글자 수 (공백 제외)
  static final String AMBIGUOUS = "여러 곳 중 못 고름:"; // 이유 앞머리 (줄여서 다시 검색하지 않는 경우)

  // 수원시 좌표 범위 (넉넉하게). 주소 검사와 함께 씀
  static final double LAT_MIN = 37.20, LAT_MAX = 37.36;
  static final double LNG_MIN = 126.90, LNG_MAX = 127.12;

  // 범위가 넓은 말 · 정해지지 않은 장소
  private static final Pattern VAGUE = Pattern.compile("일대|일원|곳곳|전역|인근|주변|미정|추후|온라인|비대면|시내|관내|각\\s|\\s등$");
  // 지역명뿐: "행궁동" "팔달구" "수원시" "광교1동" (공백 없앤 뒤 6글자 이하 + 동 · 구 · 시 · 읍 · 면 · 리 · 가)
  private static final Pattern REGION_ONLY = Pattern.compile("^[가-힣0-9]{1,5}(동|구|시|읍|면|리|가)$");
  // 결과 이름이 이보다 짧으면 "검색어가 결과 이름을 품음"으로 인정하지 않음 ("공원" 같은 흔한 말 방지)
  static final int CONTAINED_NAME_MIN = 3;
  // 가운뎃점 · 빗금 모양 구분 기호 (한 곳에 모아 MULTI · VENUE_SEP 가 같이 씀)
  //  · U+00B7 가운뎃점 / ㆍ U+318D 한글 아래아 / ・ U+30FB · ･ U+FF65 일본어 가운뎃점
  //  • U+2022 글머리 기호 / ∙ U+2219 · ⋅ U+22C5 수학 점 / "/" · ／ U+FF0F 빗금
  static final String DOT_SLASH_SEPS = "\u00B7\u318D\u30FB\uFF65\u2022\u2219\u22C5/\uFF0F";
  // 장소 여러 곳: "A, B" / "A · B" / "A / B" / "A 및 B"
  private static final Pattern MULTI = Pattern.compile("[,，" + DOT_SLASH_SEPS + "]|\\s및\\s");
  // 한 장소의 다른 표기로 볼 수 있는 구분 기호 (위 가운뎃점 · 빗금 모양)
  private static final Pattern VENUE_SEP = Pattern.compile("\\s*[" + DOT_SLASH_SEPS + "]\\s*");
  // 구분 기호 뒤 조각이 "방 · 층 이름뿐" (공백 없앤 뒤). 독립 장소가 될 수 있는 말은 넣지 않음
  //  - 숫자 층 · 호 · 관: 1층 · 지하1층 · 101호 · 2관 · B1 · 3F ("제1주차장" "제2광장" 처럼 숫자 + 장소 이름은 아님)
  //  - 영문 1~2자 + 홀 · 관: A홀 · B관
  //  - (제N · 대 · 소 · 중 · 야외 …) + 공연장 · 극장 · 강당 · 무대 · 로비 · 라운지 · 본관 · 별관 · 신관 · 상영관
  //  - (제N)(앞말 4자 이하) + 전시실 · 회의실 · 강의실 · 세미나실 · 교육실 · 연습실 / 홀(앞말 3자 이하)
  //  - "전시관" 은 넣지 않음 (한옥기술전시관처럼 따로 있는 건물)
  private static final Pattern SUB_PART = Pattern.compile(
      "(지하)?[A-Za-z]?\\d{1,4}[A-Za-z]?(층|호|관)?"
          + "|[A-Za-z]{1,2}(홀|관)"
          + "|(제\\d{1,2})?(대|소|중|야외|다목적)?(공연장|극장|강당|무대|로비|라운지|본관|별관|신관|상영관)"
          + "|(제\\d{1,2})?[가-힣A-Za-z]{0,4}(전시실|회의실|강의실|세미나실|교육실|연습실)"
          + "|[가-힣A-Za-z]{0,3}홀");

  // 괄호 부연 (안쪽에 괄호가 없는 것만, 여러 개면 모두)
  private static final Pattern PAREN = Pattern.compile("[(（][^()（）]*[)）]");
  // 앞 라벨: "집결지:" "장소 :" (한글 2~5자 + 콜론)
  private static final Pattern LEAD_LABEL = Pattern.compile("^[가-힣]{2,5}\\s*[:：]\\s*");

  // 떼어도 되는 마지막 단어: 숫자가 있거나(홀1 · 1층 · 401호) 방 · 층 · 부속 시설 이름으로 끝남
  // "관" "장" 처럼 짧은 끝말은 넣지 않음 (박물관 · 도서관 · 시장 같은 독립된 장소를 떼어 버림)
  private static final Pattern ROOM_WORD = Pattern.compile(
      ".*\\d.*|.*(층|본관|별관|신관|상영관|체육관|교육관|홀|로비|라운지|극장|구장|강당|무대|광장|마당|입구|앞|주차장|회의실|강의실|세미나실|교육실|전시실)");
  // 동 · 구 접미사가 없는 생활권 이름 (줄인 검색어가 이것이면 멈춤)
  private static final java.util.Set<String> AREA_NAMES = java.util.Set.of(
      "서수원", "북수원", "남수원", "동수원", "호매실", "광교", "영통", "행궁", "인계", "매탄", "권선", "장안", "팔달", "수원");

  private final PlaceSearchClient client;

  public PlaceGeocoder(PlaceSearchClient client) {
    this.client = client;
  }

  public Result geocode(String placeName) {
    String q = tidy(placeName);
    if (q.length() < 2) return noCoord("장소 없음");
    if (VAGUE.matcher(q).find()) return noCoord("범위가 넓은 장소");
    q = sameVenueBase(q);
    if (MULTI.matcher(q).find()) return noCoord("장소가 여러 곳");
    if (q.length() > QUERY_MAX) return noCoord("장소 이름이 너무 김");
    if (REGION_ONLY.matcher(squash(q)).matches()) return noCoord("지역 이름뿐");

    Result first = searchAndMatch(q);
    if (first.status() != Status.NO_COORD) return first;
    // 후보가 이미 여러 개였다면 줄여 봐야 더 넓어질 뿐 → 줄이지 않음
    if (first.reason() != null && first.reason().startsWith(AMBIGUOUS)) return first;

    // 못 찾음 → 뒤 단어를 떼고 다시 (정확히 같은 이름만)
    String shorter = q;
    boolean tried = false;
    for (int i = 0; i < SHORTEN_MAX; i++) {
      String last = lastWord(shorter);
      if (last == null || !ROOM_WORD.matcher(last).matches()) break; // 방 · 층 모양이 아니면 떼지 않음
      shorter = dropLastWord(shorter);
      String sq = squash(shorter);
      if (sq.length() < SHORTENED_MIN || REGION_ONLY.matcher(sq).matches() || AREA_NAMES.contains(sq)
          || VAGUE.matcher(shorter).find()) break;
      tried = true;
      PlaceSearchClient.Page page;
      try {
        page = client.search(shorter);
      } catch (RuntimeException e) {
        return error(e);
      }
      if (page == null) continue;
      final String name = shorter;
      List<PlaceCandidate> exact = page.places().stream()
          .filter(PlaceGeocoder::inSuwon).filter(c -> sameName(c.name(), name)).toList();
      if (exact.size() == 1) return new Result(Status.FOUND, exact.get(0), null);
      if (exact.size() > 1) return noCoord(AMBIGUOUS + " 이름이 같은 곳 " + exact.size() + "건 (줄인 검색어)");
    }
    return tried ? noCoord(first.reason() + " (줄여도 없음)") : first;
  }

  // 검색 1번 + 수원 · 이름 확인 (1~4번 규칙)
  private Result searchAndMatch(String q) {
    PlaceSearchClient.Page page;
    try {
      page = client.search(q);
    } catch (RuntimeException e) {
      return error(e);
    }
    if (page == null) page = new PlaceSearchClient.Page(List.of(), true);

    List<PlaceCandidate> inSuwon = page.places().stream().filter(PlaceGeocoder::inSuwon).toList();
    if (inSuwon.isEmpty()) return noCoord("수원 안 검색 결과 없음");

    List<PlaceCandidate> exact = inSuwon.stream().filter(c -> sameName(c.name(), q)).toList();
    if (exact.size() == 1) return new Result(Status.FOUND, exact.get(0), null);
    if (exact.size() > 1) return noCoord(AMBIGUOUS + " 이름이 같은 곳 " + exact.size() + "건");
    if (!page.complete()) return noCoord("결과가 더 있어 1건으로 못 좁힘");

    List<PlaceCandidate> named = inSuwon.stream().filter(c -> queryContainsName(q, c.name())).toList();
    if (named.size() == 1) return new Result(Status.FOUND, named.get(0), null);
    if (named.isEmpty()) return noCoord("이름이 맞는 결과 없음");
    return noCoord(AMBIGUOUS + " 후보 " + named.size() + "건");
  }

  // 공백 정리 + 괄호 부연 · 앞 라벨 제거
  static String tidy(String placeName) {
    if (placeName == null) return "";
    String q = placeName.strip();
    q = LEAD_LABEL.matcher(q).replaceFirst("");
    String before;
    do { // 괄호가 여러 개면 모두
      before = q;
      q = PAREN.matcher(q).replaceAll(" ");
    } while (!q.equals(before));
    return q.replaceAll("\\s+", " ").strip();
  }

  // 9번 규칙: "A · 대극장" "A 1층/2층" → "A" / "A 1층" (뒤 조각이 모두 방 · 층 이름일 때만). 아니면 그대로
  static String sameVenueBase(String q) {
    if (q.matches(".*([,，]|\\s및\\s).*")) return q; // 쉼표 · "및" 이 있으면 그대로 여러 곳
    String[] parts = VENUE_SEP.split(q, -1);
    if (parts.length < 2) return q;
    String base = parts[0].strip();
    if (base.isEmpty() || isSubPart(base)) return q;
    for (int i = 1; i < parts.length; i++) {
      if (!isSubPart(parts[i])) return q;
    }
    return base;
  }

  private static boolean isSubPart(String part) {
    String p = squash(part.strip());
    return !p.isEmpty() && SUB_PART.matcher(p).matches();
  }

  // "수원시청 본관 로비" → "로비", 한 단어면 null
  static String lastWord(String q) {
    int i = q.lastIndexOf(' ');
    return i <= 0 ? null : q.substring(i + 1);
  }

  // "수원시청 본관 로비" → "수원시청 본관", 한 단어면 null
  static String dropLastWord(String q) {
    int i = q.lastIndexOf(' ');
    return i <= 0 ? null : q.substring(0, i).strip();
  }

  // 예외 내용(키가 섞일 수 있음)은 남기지 않고 HTTP 상태 번호만
  private static Result error(RuntimeException e) {
    String status = e instanceof RestClientResponseException r ? " (HTTP " + r.getStatusCode().value() + ")" : "";
    return new Result(Status.ERROR, null, "장소 검색 실패: " + e.getClass().getSimpleName() + status);
  }

  // "가상 아트홀 2층" ⊇ "가상아트홀" (결과 이름이 너무 짧으면 불인정)
  static boolean queryContainsName(String query, String name) {
    if (name == null) return false;
    String n = squash(name);
    return n.length() >= CONTAINED_NAME_MIN && squash(query).contains(n);
  }

  public static boolean inSuwon(PlaceCandidate c) { // 수원문화DB 주소 검색 결과도 같은 기준 (SwdbPlaceResolver)
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
