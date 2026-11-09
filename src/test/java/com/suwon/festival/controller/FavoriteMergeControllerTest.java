package com.suwon.festival.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.Member;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.MemberRepository;
import com.suwon.festival.security.MergeBodyLimitFilter;
import com.suwon.festival.service.FavoriteService;

// POST /api/favorites/merge — 비회원 찜(이 기기)을 로그인한 계정에 합침
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FavoriteMergeControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private MemberRepository memberRepository;
  @Autowired private EventRepository eventRepository;

  private MockMvc mvc() {
    return MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(MergeBodyLimitFilter.class)).build();
  }

  @Test
  void 로그인하지_않으면_합치지_않음() throws Exception {
    mvc().perform(post("/api/favorites/merge").contentType(MediaType.APPLICATION_JSON).content("{\"eventIds\":[1]}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string("로그인이 필요합니다."));
  }

  @Test
  void 로그인하면_더한_개수를_돌려줌() throws Exception {
    Long memberId = memberRepository.saveAndFlush(Member.create()).getId();
    Long eventId = eventRepository.saveAndFlush(Event.builder()
        .idx(990501L).title("가상 행사 합치기").category("공연").latitude(37.28).longitude(127.01)
        .pubDate(LocalDateTime.of(2026, 11, 1, 12, 0))
        .startDate(LocalDate.of(2026, 11, 5)).endDate(LocalDate.of(2026, 11, 5)).build()).getId();
    MockHttpSession session = new MockHttpSession();
    session.setAttribute("memberId", memberId);

    mvc().perform(post("/api/favorites/merge").session(session).contentType(MediaType.APPLICATION_JSON)
            .content("{\"eventIds\":[" + eventId + "," + eventId + ",-1]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.added").value(1));
  }

  private MockHttpSession loggedIn() {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute("memberId", memberRepository.saveAndFlush(Member.create()).getId());
    return session;
  }

  @Test
  void 숫자가_아닌_번호는_400() throws Exception {
    mvc().perform(post("/api/favorites/merge").session(loggedIn()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"eventIds\":[\"abc\"]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 번호가_한도보다_많으면_400() throws Exception {
    StringBuilder ids = new StringBuilder();
    for (int i = 1; i <= FavoriteService.MERGE_MAX + 1; i++) ids.append(i == 1 ? "" : ",").append(i);
    mvc().perform(post("/api/favorites/merge").session(loggedIn()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"eventIds\":[" + ids + "]}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string("합칠 찜이 너무 많습니다."));
  }

  @Test
  void 본문이_너무_크면_읽기_전에_413() throws Exception {
    String body = "{\"eventIds\":[" + "1,".repeat(5000) + "1]}"; // 약 10KB
    mvc().perform(post("/api/favorites/merge").session(loggedIn()).contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isPayloadTooLarge());
  }

  @Test
  void 동시에_합쳐_고유_제약에_걸리면_409() throws Exception {
    FavoriteService service = mock(FavoriteService.class);
    when(service.mergeFavorites(any(), anyList())).thenThrow(new DataIntegrityViolationException("uk_favorite_member_event"));
    MockHttpSession session = new MockHttpSession();
    session.setAttribute("memberId", 1L);

    MockMvcBuilders.standaloneSetup(new FavoriteController(service))
        .setControllerAdvice(new GlobalExceptionHandler()).build()
        .perform(post("/api/favorites/merge").session(session).contentType(MediaType.APPLICATION_JSON)
            .content("{\"eventIds\":[1]}"))
        .andExpect(status().isConflict());
  }
}
