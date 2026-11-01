package com.suwon.festival.service.merge;

import java.time.LocalDate;
import java.util.List;

import com.suwon.festival.entity.Event;

// 합치기 계산 결과 (설계 5번). 미리보기 API 응답 · 동기화 결과에 그대로 씀
// groups: 합칠 묶음 / conflicts: 재단 행사가 2개 이상 끼어 합치지 않은 묶음 (기준이 느슨하다는 신호)
public record EventMergeResult(
    int groupCount,
    int hiddenCount,
    int conflictCount,
    List<Group> groups,
    List<List<Item>> conflicts) {

  public record Group(Item representative, List<Item> hidden) {
  }

  // 사람이 눈으로 맞는지 판단할 때 필요한 칸만
  public record Item(
      Long id,
      String source,
      String title,
      String place,
      LocalDate startDate,
      LocalDate endDate,
      String link) {

    static Item from(Event e) {
      return new Item(e.getId(), e.getSource(), e.getTitle(), e.getPlace(), e.getStartDate(), e.getEndDate(), e.getLink());
    }
  }

  static EventMergeResult of(List<Group> groups, List<List<Item>> conflicts) {
    int hidden = groups.stream().mapToInt(g -> g.hidden().size()).sum();
    return new EventMergeResult(groups.size(), hidden, conflicts.size(), groups, conflicts);
  }
}
