package com.suwon.festival.service.merge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.suwon.festival.entity.Event;

// 같은 행사끼리 묶고 대표를 고름 (설계 3번 "묶음과 대표")
// - A=B, B=C 면 A·B·C 한 묶음 (이어 붙이기)
// - 대표 우선순위: 재단 → 인스타 개별 글 → 인스타 모음 글 → 그 밖의 출처, 같으면 id 작은 쪽
// - 인스타끼리면 기간을 전부 덮는 행을 대표로, 그런 행이 없으면 대표 기간을 벗어난 행은 숨기지 않고 그대로 둠 (행사가 지도에서 사라지지 않게)
// - 묶음에 재단 행사가 2개 이상이면 합치지 않음 (기준이 너무 느슨하다는 신호 → conflicts 로 돌려줘 로그에 남김)
// - DB 를 건드리지 않는 순수 계산. 같은 입력이면 항상 같은 결과 (입력 순서와도 무관)
public final class EventGrouper {

  private EventGrouper() {
  }

  // 묶음 1개: 대표 + 숨길 행들(대표 제외)
  public record MergeGroup(MergeCandidate representative, List<MergeCandidate> hidden) {
  }

  // merged: 합칠 묶음 / conflicts: 재단 2개 이상이라 합치지 않은 묶음
  public record Result(List<MergeGroup> merged, List<List<MergeCandidate>> conflicts) {
  }

  static final Comparator<MergeCandidate> PRIORITY = Comparator
      .comparingInt(EventGrouper::rank)
      .thenComparing(MergeCandidate::id, Comparator.nullsLast(Comparator.naturalOrder()));

  public static Result group(List<MergeCandidate> candidates) {
    List<MergeCandidate> list = new ArrayList<>(candidates);
    list.sort(Comparator.comparing(MergeCandidate::id, Comparator.nullsLast(Comparator.naturalOrder())));

    int[] parent = new int[list.size()];
    for (int i = 0; i < parent.length; i++) {
      parent[i] = i;
    }
    for (int i = 0; i < list.size(); i++) {
      for (int j = i + 1; j < list.size(); j++) {
        if (EventMatcher.isSameEvent(list.get(i), list.get(j))) {
          union(parent, i, j);
        }
      }
    }

    Map<Integer, List<MergeCandidate>> byRoot = new LinkedHashMap<>();
    for (int i = 0; i < list.size(); i++) {
      byRoot.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(list.get(i));
    }

    List<MergeGroup> merged = new ArrayList<>();
    List<List<MergeCandidate>> conflicts = new ArrayList<>();
    for (List<MergeCandidate> members : byRoot.values()) {
      if (members.size() < 2) {
        continue;
      }
      long foundation = members.stream().filter(MergeCandidate::isFoundation).count();
      if (foundation >= 2) {
        conflicts.add(List.copyOf(members));
        continue;
      }
      List<MergeCandidate> sorted = new ArrayList<>(members);
      sorted.sort(PRIORITY);
      MergeCandidate representative = chooseRepresentative(sorted);
      // 대표 기간을 벗어난 행은 숨기지 않음: 화면은 대표 날짜로 지난 행사를 거르므로, 숨기면 남은 기간 · 다른 회차가 지도에서 사라짐
      List<MergeCandidate> hidden = sorted.stream()
          .filter(c -> c != representative && EventMatcher.periodWithin(c, representative))
          .toList();
      if (!hidden.isEmpty()) {
        merged.add(new MergeGroup(representative, hidden));
      }
    }
    return new Result(List.copyOf(merged), List.copyOf(conflicts));
  }

  // 우선순위 1등이 대표. 단 재단이 아니면서 다른 행의 기간을 벗어나면, 기간을 전부 덮는 행 중 우선순위가 가장 높은 행
  // (개별 글 11/1 + 모음 글 ~12/31 → 모음 글). 재단은 날짜가 더 정확하므로 그대로 대표. 전부 덮는 행이 없어도 1등 유지
  private static MergeCandidate chooseRepresentative(List<MergeCandidate> sorted) {
    MergeCandidate first = sorted.get(0);
    if (first.isFoundation()) {
      return first;
    }
    return sorted.stream()
        .filter(c -> sorted.stream().allMatch(other -> EventMatcher.periodWithin(other, c)))
        .findFirst()
        .orElse(first);
  }

  // 작을수록 대표가 되기 쉬움
  static int rank(MergeCandidate c) {
    if (c.isFoundation()) {
      return 0;
    }
    if (Event.SOURCE_INSTAGRAM.equals(c.source())) {
      return c.roundup() ? 2 : 1;
    }
    return 3; // 새 출처(설계 9번) — 들어오면 여기에 우선순위 추가
  }

  private static int find(int[] parent, int i) {
    while (parent[i] != i) {
      parent[i] = parent[parent[i]];
      i = parent[i];
    }
    return i;
  }

  private static void union(int[] parent, int a, int b) {
    int ra = find(parent, a);
    int rb = find(parent, b);
    if (ra != rb) {
      parent[Math.max(ra, rb)] = Math.min(ra, rb);
    }
  }
}
