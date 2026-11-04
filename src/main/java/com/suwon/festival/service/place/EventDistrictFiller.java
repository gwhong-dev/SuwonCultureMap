package com.suwon.festival.service.place;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.suwon.festival.entity.Event;
import com.suwon.festival.repository.EventRepository;

import lombok.RequiredArgsConstructor;

// 동기화 뒤 구를 모르는 행사(district = null)만 좌표 → 구로 채움 (계획 D1)
// - 이미 채운 행사는 다시 부르지 않음 → 평소에는 새 행사 · 좌표가 바뀐 행사만 호출
// - 호출 실패는 비워 둠 → 다음 동기화 때 다시. 연속 실패면 키 · 한도 문제로 보고 이번 실행은 멈춤
// - 동기화 결과를 실패로 만들지 않음 (예외를 밖으로 던지지 않음)
// ⚠️ @Transactional 을 붙이지 말 것: 한 줄씩 각자 저장해야 중간 실패가 앞 결과를 되돌리지 않음
@Component
@RequiredArgsConstructor
public class EventDistrictFiller {

  private static final Logger log = LoggerFactory.getLogger(EventDistrictFiller.class);

  static final int MAX_PER_RUN = 500;          // 한 번에 부르는 최대 수 (재단 + 인스타 전부여도 200건 안팎)
  static final int MAX_CONSECUTIVE_FAILURES = 3;
  static final Set<String> SUWON_DISTRICTS = Set.of("장안구", "권선구", "팔달구", "영통구");

  private final EventRepository eventRepository;
  private final RegionLookupClient regionLookupClient;

  public record Summary(int filled, int outside, int failed, boolean stopped) {

    public String text() {
      return "구 정보 · 채움 " + filled + " · 수원 밖 " + outside + " · 실패 " + failed
          + (stopped ? " (연속 실패로 중단)" : "");
    }
  }

  public Summary fillMissing() {
    int filled = 0;
    int outside = 0;
    int failed = 0;
    int consecutiveFailures = 0;
    boolean stopped = false;
    try {
      List<Event> targets = eventRepository.findByDistrictIsNullOrderByIdDesc(PageRequest.of(0, MAX_PER_RUN));
      for (Event event : targets) {
        if (event.getLatitude() == null || event.getLongitude() == null) continue;
        String district;
        try {
          district = toDistrict(regionLookupClient.lookup(event.getLatitude(), event.getLongitude()));
          consecutiveFailures = 0;
        } catch (RuntimeException e) {
          failed++;
          // 메시지는 남기지 않음 (응답 본문 · 주소가 섞일 수 있음) → 종류만
          log.warn("구 정보 조회 실패: id={} {}", event.getId(), e.getClass().getSimpleName());
          if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            stopped = true;
            break;
          }
          continue;
        }
        if (district == null) {
          // 수원인데 구 이름 형식이 예상과 다름 → 틀린 값을 굳히지 않고 비워 둠 (응답 형식 변경 확인용 로그)
          failed++;
          log.warn("구 이름 형식 모름: id={}", event.getId());
          continue;
        }
        int updated = eventRepository.saveDistrict(event.getId(), event.getLatitude(), event.getLongitude(), district);
        if (updated == 0) continue; // 그사이 좌표가 바뀜 → 다음 실행 때 새 좌표로
        if (Event.DISTRICT_OUTSIDE.equals(district)) outside++;
        else filled++;
      }
    } catch (RuntimeException e) {
      // DB 오류 등 — 동기화 · 합치기는 계속 진행
      log.warn("구 정보 채우기 실패 {}", e.getClass().getSimpleName());
      stopped = true;
    }
    Summary summary = new Summary(filled, outside, failed, stopped);
    if (failed > 0 || stopped) log.warn(summary.text());
    else log.info(summary.text());
    return summary;
  }

  // "경기도" + "수원시 팔달구" → "팔달구" / 다른 시 · 도 · 바다 → 수원 밖
  // 수원인데 구를 못 읽으면 null (모르는 형식 — 수원 밖으로 굳히지 않음)
  static String toDistrict(RegionLookupClient.Region region) {
    if (region == null || region.sigungu() == null) return Event.DISTRICT_OUTSIDE;
    if (region.sido() != null && !region.sido().startsWith("경기")) return Event.DISTRICT_OUTSIDE;
    String sigungu = region.sigungu().strip();
    if (!sigungu.startsWith("수원")) return Event.DISTRICT_OUTSIDE;
    String[] parts = sigungu.split("\\s+");
    if (parts.length == 2 && "수원시".equals(parts[0]) && SUWON_DISTRICTS.contains(parts[1])) return parts[1];
    return null;
  }
}
