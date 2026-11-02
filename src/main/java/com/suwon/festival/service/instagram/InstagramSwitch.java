package com.suwon.festival.service.instagram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.suwon.festival.entity.Event;

// 인스타 수집 · 표시를 한 번에 켜고 끄는 스위치 (docs/decisions/2026-11-02-instagram-resume-official.md)
//  - 꺼져 있으면 Meta API 를 호출하지 않고, 인스타 행사를 어떤 응답으로도 내보내지 않음
//  - 저장된 행은 지우지 않음 → 켜면 코드 변경 없이 그대로 돌아옴
@Component
public class InstagramSwitch {

  private final boolean enabled;

  public InstagramSwitch(@Value("${instagram.sync.enabled:false}") boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isEnabled() {
    return enabled;
  }

  // 이 행사를 밖으로 내보내도 되는지
  public boolean isVisible(Event event) {
    return enabled || !Event.SOURCE_INSTAGRAM.equals(event.getSource());
  }
}
