package com.suwon.festival.service.swdb;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.suwon.festival.service.place.AddressSearchClient;
import com.suwon.festival.service.place.PlaceCandidate;
import com.suwon.festival.service.place.PlaceGeocoder;

// 수원문화DB 행사 장소(대부분 주소) → 지도 좌표 + 카드에 보일 장소 줄 + 상세 안내 (결정 docs/decisions/2026-11-13-swdb-crawling.md)
//  1) 수원 밖 주소("경기 화성시 …")는 OUTSIDE (저장 안 함)
//  2) 주소가 1개: 도로명 · 지번만 떼어 카카오 주소 검색 → 수원 안 결과 1건째. 실패하면 남은 장소 이름으로 장소 검색
//  3) 장소가 여러 곳("A, B, C" · 주소 뒤에 장소가 2곳 이상): 제외하지 않음 — 좌표를 찾은 첫 장소에 핀,
//     카드 = "여러 장소 · A 외 N곳", 상세 = 여러 곳이라 원문 확인 필수 안내 (11/13 사용자 결정)
//  4) 넓은 범위("수원역 일대(이동형)"): 기준 장소("수원역")에 핀, 카드 = 원문 그대로, 상세 = 같은 안내
//  5) 그래도 못 찾으면 NO_COORD (저장 안 함), 검색 호출 실패는 ERROR (다음 실행에서 다시)
@Component
public class SwdbPlaceResolver {

  public enum Status { FOUND, NO_COORD, OUTSIDE, ERROR }

  // place = 카드 · 상세의 장소 줄, note = 상세 안내(여러 장소 · 넓은 범위일 때만, 아니면 null)
  public record Result(Status status, Double lat, Double lng, String place, String note, String reason) {

    static Result found(PlaceCandidate c, String place, String note) {
      return new Result(Status.FOUND, c.lat(), c.lng(), place, note, null);
    }

    static Result fail(Status status, String reason) {
      return new Result(status, null, null, null, null, reason);
    }
  }

  static final String MULTI_NOTE = "⚠ 장소가 여러 곳이에요. 날짜마다 장소가 다를 수 있어 원문에서 꼭 확인하세요.";
  static final String WIDE_NOTE = "⚠ 장소가 한 곳으로 정해져 있지 않아요. 지도 위치는 기준 장소예요. 원문에서 꼭 확인하세요.";
  static final int PLACE_MAX = 500; // event.place 칸 길이

  // 수원시 도로명 주소("수원시 영통구 반달로7번길 40") 또는 지번 주소("수원시 팔달구 인계동 1127", "하동 1023")
  // 끝 번호 뒤에 숫자 · "번길" 이 이어지면 건물번호가 아님("정조로 825번길" → 주소로 안 봄, 검증 지적)
  private static final Pattern ADDRESS = Pattern.compile(
      "수원(?:특례)?시\\s+\\S+구\\s+(?:\\S+(?:로|길)(?:\\s*\\d+번?길)?\\s*\\d+(?:-\\d+)?"
          + "|\\S+(?:동|가)\\d*\\s*(?:산\\s*)?\\d+(?:-\\d+)?)(?![\\d번길])");
  // 수원 밖: 도 이름 뒤의 시 · 군("경기 화성시 ") 또는 광역 · 특별시로 시작("서울 중구 ")
  // 도 이름을 붙여야 함 — "기획전시 공간" 의 "전시 " 같은 말을 시 이름으로 잘못 읽지 않게 (검증 지적)
  private static final Pattern CITY = Pattern.compile(
      "(?:경기도?|강원(?:특별자치)?도?|충청남도|충남|충청북도|충북)\\s+([가-힣]{1,4}?)(?:특례시|시|군)\\s");
  private static final Pattern METRO = Pattern.compile(
      "(?:^|[\\s(])(?:서울|인천|부산|대구|광주|대전|울산|세종)(?:특별시|광역시|특별자치시)?\\s");
  private static final Pattern WIDE = Pattern.compile("일대|일원|곳곳|이동형|순회");
  private static final Pattern WIDE_BASE = Pattern.compile("^(.+?)\\s*(?:일대|일원|곳곳)");
  private static final Pattern PAREN = Pattern.compile("\\([^)]*\\)|（[^）]*）");
  private static final Pattern LEADING_DATE = Pattern.compile("^\\d{1,2}/\\d{1,2}\\s*");
  private static final Pattern SEPARATOR = Pattern.compile("\\s*(?:,|，|\\s및\\s|·|ㆍ)\\s*");
  // 방 · 층 조각("2층", "407-1호", "B1")은 장소 수에 넣지 않음
  private static final Pattern ROOM = Pattern.compile("^(?:지하\\s*)?\\d+\\s*층.*|.*\\d+(?:-\\d+)?\\s*호.*|^B\\d+.*|^\\d+F$");
  private static final Pattern LEADING_PROVINCE = Pattern.compile("^(?:경기도|경기)\\s+");

  private final AddressSearchClient addressClient;
  private final PlaceGeocoder geocoder;

  public SwdbPlaceResolver(AddressSearchClient addressClient, PlaceGeocoder geocoder) {
    this.addressClient = addressClient;
    this.geocoder = geocoder;
  }

  public Result resolve(String raw) {
    String text = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
    if (text.length() < 2) return Result.fail(Status.NO_COORD, "장소 없음");
    if (outsideSuwon(text)) return Result.fail(Status.OUTSIDE, "수원 밖");

    List<String> addresses = addresses(text);
    if (!addresses.isEmpty()) {
      return resolveWithAddress(text, addresses);
    }
    if (WIDE.matcher(text).find()) {
      return resolveWide(text);
    }
    List<String> names = placeNames(text);
    if (names.size() >= 2) {
      return resolveMulti(text, names);
    }
    return byName(text, label(text), null);
  }

  // 주소가 있음: 주소 뒤(또는 앞)에 장소 이름이 2곳 이상이면 여러 장소
  private Result resolveWithAddress(String text, List<String> addresses) {
    String rest = text;
    for (String a : addresses) rest = rest.replace(a, " ");
    rest = rest.replaceAll("^\\s*(?:경기도|경기)\\s*", " ");
    List<String> others = placeNames(rest);
    boolean multi = addresses.size() >= 2 || others.size() >= 2;
    String place = multi ? multiLabel(others.isEmpty() ? addresses : others,
        Math.max(addresses.size(), others.size())) : label(text);
    String note = multi ? MULTI_NOTE + "\n장소 전체: " + text : null;
    for (String address : addresses) {
      PlaceCandidate c;
      try {
        c = firstInSuwon(addressClient.search(address));
      } catch (RuntimeException e) {
        return Result.fail(Status.ERROR, "주소 검색 실패 " + e.getClass().getSimpleName());
      }
      if (c != null) return Result.found(c, place, note);
    }
    // 주소로 못 찾음 → 남은 장소 이름으로 한 번 더 (예: "실험공간UZ 경기 수원시 …" 의 "실험공간UZ")
    if (!others.isEmpty()) return byName(others.get(0), place, note);
    return Result.fail(Status.NO_COORD, "주소 검색 결과 없음");
  }

  // "수원역 일대(이동형 공연 예정)" → "수원역" 에 핀
  private Result resolveWide(String text) {
    String noParen = PAREN.matcher(text).replaceAll(" ").strip();
    Matcher m = WIDE_BASE.matcher(noParen);
    String base = m.find() ? m.group(1).strip() : noParen.replaceAll(WIDE.pattern(), " ").strip();
    if (base.length() < 2) return Result.fail(Status.NO_COORD, "기준 장소 없음");
    List<String> names = placeNames(base);
    String first = names.isEmpty() ? base : names.get(0);
    return byName(first, label(text), WIDE_NOTE + "\n장소 원문: " + text);
  }

  // "A, B, C" → 좌표를 찾은 첫 장소. 카드 · 길찾기 이름도 핀을 찍은 장소로 ("여러 장소 · B 외 2곳", 검증 지적)
  private Result resolveMulti(String text, List<String> names) {
    String note = MULTI_NOTE + "\n장소 전체: " + text;
    Result last = Result.fail(Status.NO_COORD, "여러 장소 모두 못 찾음");
    for (String name : names) {
      List<String> ordered = new ArrayList<>(names);
      ordered.remove(name);
      ordered.add(0, name);
      Result r = byName(name, multiLabel(ordered, names.size()), note);
      if (r.status() == Status.FOUND || r.status() == Status.ERROR) return r;
      last = r;
    }
    return last;
  }

  private Result byName(String name, String place, String note) {
    PlaceGeocoder.Result g = geocoder.geocode(PAREN.matcher(name).replaceAll(" ").strip());
    return switch (g.status()) {
      case FOUND -> Result.found(g.place(), place, note);
      case ERROR -> Result.fail(Status.ERROR, g.reason());
      case NO_COORD -> Result.fail(Status.NO_COORD, g.reason());
    };
  }

  static boolean outsideSuwon(String text) {
    String t = text + " ";
    Matcher m = CITY.matcher(t);
    boolean otherCity = false;
    while (m.find()) {
      if (m.group(1).startsWith("수원")) return false; // 수원시 · 수원특례시
      otherCity = true;
    }
    return otherCity || (METRO.matcher(t).find() && !t.contains("수원"));
  }

  static List<String> addresses(String text) {
    Set<String> found = new LinkedHashSet<>();
    Matcher m = ADDRESS.matcher(text);
    while (m.find()) found.add(m.group().replaceAll("\\s+", " ").strip());
    return new ArrayList<>(found);
  }

  // 쉼표 · "및" · 가운뎃점으로 나눈 장소 이름 (날짜 머리 "7/9 " · 괄호 · 방 · 층 조각은 뺌)
  static List<String> placeNames(String text) {
    List<String> names = new ArrayList<>();
    for (String part : SEPARATOR.split(text)) {
      String p = LEADING_DATE.matcher(part.strip()).replaceAll("");
      p = PAREN.matcher(p).replaceAll(" ").replaceAll("\\s+", " ").strip();
      if (p.length() < 2 || ROOM.matcher(p).matches()) continue;
      names.add(p);
    }
    return names;
  }

  static String multiLabel(List<String> names, int count) {
    String first = names.get(0);
    if (first.length() > 20) first = first.substring(0, 20) + "…";
    return count <= 1 ? first : "여러 장소 · " + first + " 외 " + (count - 1) + "곳";
  }

  // 카드 장소 줄: "경기 " 머리를 떼고 칸 길이에 맞춤
  static String label(String text) {
    String s = LEADING_PROVINCE.matcher(text).replaceAll("").strip();
    return s.length() <= PLACE_MAX ? s : s.substring(0, PLACE_MAX - 1) + "…";
  }

  private static PlaceCandidate firstInSuwon(List<PlaceCandidate> candidates) {
    if (candidates == null) return null;
    return candidates.stream().filter(PlaceGeocoder::inSuwon).findFirst().orElse(null);
  }
}
