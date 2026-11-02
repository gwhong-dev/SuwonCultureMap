package com.suwon.festival.service.instagram;

import java.util.Locale;
import java.util.Set;

// 수집을 허용하는 수원시 · 문화기관 공식 계정 (docs/decisions/2026-11-02-instagram-resume-official.md)
//  - 설정(instagram.accounts)에 개인 계정을 넣어도 여기 없으면 Meta API 를 호출하지 않음
//  - 계정을 늘리려면 이 목록을 고치는 PR 로 (결정 문서도 함께 갱신)
public final class OfficialInstagramAccounts {

  // 10/31 실측한 5계정 (scripts/set-instagram-accounts.ps1 의 "전체 5계정"과 같게 유지)
  public static final Set<String> NAMES = Set.of(
      "suwon_city", "sw_culture", "suwonskartrium", "suwontraditional", "suwon_media");

  private OfficialInstagramAccounts() {}

  // 인스타 계정 이름은 대소문자를 구분하지 않음 → 앞의 @ 를 떼고 소문자로
  public static String normalize(String account) {
    if (account == null) return "";
    String name = account.strip();
    if (name.startsWith("@")) name = name.substring(1);
    return name.toLowerCase(Locale.ROOT);
  }

  public static boolean isOfficial(String account) {
    return NAMES.contains(normalize(account));
  }
}
