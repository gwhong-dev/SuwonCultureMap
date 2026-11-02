package com.suwon.festival.service.instagram;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class OfficialInstagramAccountsTest {

  @Test
  void 공식_계정만_허용하고_이름은_정리해서_비교() {
    assertThat(OfficialInstagramAccounts.isOfficial("suwon_city")).isTrue();
    assertThat(OfficialInstagramAccounts.isOfficial(" @SUWON_CITY ")).isTrue();
    assertThat(OfficialInstagramAccounts.isOfficial("some_influencer")).isFalse();
    assertThat(OfficialInstagramAccounts.isOfficial("")).isFalse();
    assertThat(OfficialInstagramAccounts.isOfficial(null)).isFalse();
  }

  @Test
  void 기본_설정과_PC_설정_스크립트의_계정이_모두_공식_목록에_있음() throws Exception {
    String props = Files.readString(Path.of("src/main/resources/application.properties"), StandardCharsets.UTF_8);
    Matcher def = Pattern.compile("instagram\\.accounts=\\$\\{INSTAGRAM_ACCOUNTS:([^}]*)}").matcher(props);
    assertThat(def.find()).isTrue();
    assertThat(OfficialInstagramAccounts.isOfficial(def.group(1))).isTrue();

    String ps1 = Files.readString(Path.of("scripts/set-instagram-accounts.ps1"), StandardCharsets.UTF_8);
    Matcher m = Pattern.compile("\\$measure\\s*=\\s*'([^']*)'").matcher(ps1);
    assertThat(m.find()).isTrue();
    Matcher all = Pattern.compile("\\$all\\s*=\\s*'([^']*)'").matcher(ps1);
    assertThat(all.find()).isTrue();
    Set<String> scriptAll = Arrays.stream((all.group(1) + m.group(1)).split(","))
        .map(String::strip).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    assertThat(scriptAll).containsExactlyInAnyOrderElementsOf(OfficialInstagramAccounts.NAMES);
  }
}
