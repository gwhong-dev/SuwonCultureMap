package com.suwon.festival.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

// 랜덤 닉네임: 30 × 30 조합, 길이 · 문자 규칙, 겹칠 때 처리
class NicknameGeneratorTest {

  @Test
  void 단어는_30개씩_겹치지_않음() {
    assertThat(NicknameGenerator.ADJECTIVES).hasSize(30).doesNotHaveDuplicates();
    assertThat(NicknameGenerator.LANDMARKS).hasSize(30).doesNotHaveDuplicates();
  }

  @Test
  void 모든_조합은_숫자_2자리를_붙여도_규칙을_지킴() {
    for (String a : NicknameGenerator.ADJECTIVES) {
      for (String l : NicknameGenerator.LANDMARKS) {
        String name = a + " " + l;
        assertThat(name.length()).as(name).isLessThanOrEqualTo(10);
        assertThat(NicknameGenerator.validate(name)).as(name).isNull();
        assertThat(NicknameGenerator.validate(name + "99")).as(name).isNull();
      }
    }
  }

  @Test
  void 쓰는_이름은_피함() {
    NicknameGenerator gen = new NicknameGenerator(new Random(1));
    Set<String> taken = new HashSet<>();
    for (int i = 0; i < 200; i++) {
      String name = gen.generate(taken::contains);
      assertThat(taken.add(name)).as(name).isTrue();
    }
  }

  @Test
  void 조합이_다_차면_숫자_2자리를_붙임() {
    NicknameGenerator gen = new NicknameGenerator(new Random(2));
    String name = gen.generate(n -> !n.matches(".*\\d\\d$")); // 숫자 없는 이름은 모두 쓰는 중
    assertThat(name).matches(".+ .+[1-9]\\d$");
  }

  @Test
  void 입력_정리와_규칙() {
    assertThat(NicknameGenerator.normalize("  수원   러버  ")).isEqualTo("수원 러버");
    assertThat(NicknameGenerator.normalize(null)).isEmpty();
    assertThat(NicknameGenerator.validate("가")).isNotNull();
    assertThat(NicknameGenerator.validate("가나다라마바사아자차카타파")).isNotNull(); // 13자
    assertThat(NicknameGenerator.validate("수원<b>")).isNotNull();
    assertThat(NicknameGenerator.validate("Suwon 2026")).isNull();
  }
}
