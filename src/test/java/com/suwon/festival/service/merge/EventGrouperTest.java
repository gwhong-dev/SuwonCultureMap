package com.suwon.festival.service.merge;

import static com.suwon.festival.service.merge.MergeFixtures.LAT;
import static com.suwon.festival.service.merge.MergeFixtures.LNG;
import static com.suwon.festival.service.merge.MergeFixtures.insta;
import static com.suwon.festival.service.merge.MergeFixtures.swcf;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.suwon.festival.service.merge.EventGrouper.MergeGroup;
import com.suwon.festival.service.merge.EventGrouper.Result;

// 묶음 · 대표 (설계 3번 "묶음과 대표" · 6번)
class EventGrouperTest {

  @Test
  void 재단_1_인스타_2는_재단이_대표() {
    var f = swcf(10, "가을 음악회", "2026-11-05", "2026-11-07");
    var i1 = insta(3, "가을음악회", "2026-11-05", null, false);
    var i2 = insta(4, "<가을 음악회>", "2026-11-06", null, true);

    Result r = EventGrouper.group(List.of(i1, f, i2));

    assertThat(r.conflicts()).isEmpty();
    assertThat(r.merged()).hasSize(1);
    MergeGroup g = r.merged().get(0);
    assertThat(g.representative()).isEqualTo(f);
    assertThat(g.hidden()).containsExactly(i1, i2);
  }

  @Test
  void 사슬로_이어지면_한_묶음() {
    // A(11/1~3) = B(11/3~5) = C(11/5) 지만 A 와 C 는 날짜가 안 겹침 → 그래도 한 묶음
    var a = insta(1, "가을 음악회", "2026-11-01", "2026-11-03", true);
    var b = insta(2, "가을 음악회", "2026-11-03", "2026-11-05", true);
    var c = insta(3, "가을 음악회", "2026-11-05", null, true);
    assertThat(EventMatcher.isSameEvent(a, c)).isFalse();

    Result r = EventGrouper.group(List.of(a, b, c));

    assertThat(r.merged()).hasSize(1);
    assertThat(r.merged().get(0).representative()).isEqualTo(a);
    assertThat(r.merged().get(0).hidden()).containsExactly(b, c);
  }

  @Test
  void 대표_우선순위_재단_개별글_모음글_id() {
    var roundup = insta(1, "가을 음악회", "2026-11-05", null, true);
    var single = insta(2, "가을 음악회", "2026-11-05", null, false);
    var other = new MergeCandidate(0L, "OTHER", "가을 음악회", "가상 아트홀", LAT, LNG,
        LocalDate.parse("2026-11-05"), null, false);

    // 재단 없음 → 개별 글이 모음 글 · 새 출처보다 우선
    assertThat(EventGrouper.group(List.of(roundup, single, other)).merged().get(0).representative())
        .isEqualTo(single);
    // 개별 글도 없음 → 모음 글이 새 출처보다 우선
    assertThat(EventGrouper.group(List.of(roundup, other)).merged().get(0).representative())
        .isEqualTo(roundup);
    // 같은 순위 → id 작은 쪽
    var single2 = insta(5, "가을 음악회", "2026-11-05", null, false);
    assertThat(EventGrouper.group(List.of(single2, single)).merged().get(0).representative())
        .isEqualTo(single);
    // 재단이 끼면 재단 (id 가 커도)
    var f = swcf(99, "가을 음악회", "2026-11-05", null);
    assertThat(EventGrouper.group(List.of(roundup, single, f)).merged().get(0).representative())
        .isEqualTo(f);
  }

  @Test
  void 재단_2개가_낀_묶음은_합치지_않음() {
    var f1 = swcf(1, "가을 음악회", "2026-11-05", null);
    var f2 = swcf(2, "가을 음악회", "2026-11-05", null);
    var i = insta(3, "가을 음악회", "2026-11-05", null, false);

    Result r = EventGrouper.group(List.of(f1, f2, i));

    assertThat(r.merged()).isEmpty();
    assertThat(r.conflicts()).hasSize(1);
    assertThat(r.conflicts().get(0)).containsExactly(f1, f2, i);
  }

  @Test
  void 혼자인_행사는_결과에_없음() {
    var a = swcf(1, "가을 음악회", "2026-11-05", null);
    var b = insta(2, "겨울 빛 축제", "2026-11-05", null, false);
    var c = insta(3, "가을 음악회", null, null, false); // 시작일 없음 → 비교 제외

    Result r = EventGrouper.group(List.of(a, b, c));

    assertThat(r.merged()).isEmpty();
    assertThat(r.conflicts()).isEmpty();
  }

  @Test
  void 입력_순서가_달라도_같은_결과() {
    List<MergeCandidate> list = new ArrayList<>(List.of(
        swcf(1, "가을 음악회", "2026-11-05", null),
        insta(2, "가을음악회", "2026-11-05", null, true),
        insta(3, "겨울 빛 축제", "2026-12-24", null, false),
        insta(4, "겨울빛축제", "2026-12-24", "2026-12-31", true)));
    Result first = EventGrouper.group(list);
    Collections.reverse(list);
    assertThat(EventGrouper.group(list)).isEqualTo(first);
    assertThat(first.merged()).hasSize(2);
  }
}
