package com.suwon.festival.service.caption;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.suwon.festival.entity.CaptionAiResult;
import com.suwon.festival.entity.CaptionAiResult.Fields;
import com.suwon.festival.entity.CaptionAiResult.Outcome;
import com.suwon.festival.repository.CaptionAiResultRepository;

// 캡션 AI 앞단: 결과 보관(같은 블록은 다시 안 부름) + 하루 호출 상한
// - 동기화는 하루 2번 최근 게시물을 다시 읽음 → 보관 없이 붙이면 같은 블록에 AI 를 매번 부름
// - 보관 키 = 블록 원문 해시 (원문 자체는 저장 안 함, V11)
// - 상한을 넘거나 호출이 실패하면 예외 → CaptionParser 가 PENDING 으로 두고 다음 실행에서 다시 시도. 실패는 보관하지 않음
// - 꺼낸 값도 CaptionParser 가 ParsedEventValidator 로 다시 검사 (보관 값을 그대로 믿지 않음)
public class CachedCaptionAiClient implements CaptionAiClient {

  private static final Logger log = LoggerFactory.getLogger(CachedCaptionAiClient.class);
  private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

  private final CaptionAiClient ai;
  private final String model;
  private final CaptionAiResultRepository repository;
  private final Clock clock;
  private final int dailyLimit;

  private LocalDate budgetDay; // budget* 는 reserveCall 안에서만 (synchronized)
  private int budgetUsed;

  public CachedCaptionAiClient(CaptionAiClient ai, String model, CaptionAiResultRepository repository, Clock clock,
      int dailyLimit) {
    this.ai = ai;
    this.model = model;
    this.repository = repository;
    this.clock = clock;
    this.dailyLimit = dailyLimit;
  }

  @Override
  public Optional<ParsedEvent> parse(String blockText) {
    String hash = hash(blockText);
    Optional<CaptionAiResult> saved = repository.findById(hash);
    if (saved.isPresent()) return toEvent(saved.get());

    if (!reserveCall()) throw new LimitReachedException(dailyLimit);
    Optional<ParsedEvent> result = ai.parse(blockText); // 실패는 예외로 올라감 → 보관 안 함

    Outcome outcome = result.isEmpty() ? Outcome.UNREADABLE
        : Boolean.FALSE.equals(result.get().isEvent()) ? Outcome.NOT_EVENT
        : result.get().isEvent() == null ? Outcome.UNREADABLE
        : Outcome.EVENT;
    Fields fields = outcome == Outcome.EVENT ? fieldsOf(result.get()) : Fields.EMPTY;
    repository.save(CaptionAiResult.of(hash, outcome, fields, model, LocalDateTime.now(clock.withZone(KOREA))));
    return toEvent(outcome, fields);
  }

  private static Optional<ParsedEvent> toEvent(CaptionAiResult r) {
    return toEvent(r.getOutcome(), r.fields());
  }

  private static Optional<ParsedEvent> toEvent(Outcome outcome, Fields f) {
    return switch (outcome) {
      case EVENT -> Optional.of(new ParsedEvent(true, f.title(), f.category(), f.startDate(), f.endDate(),
          f.timeText(), f.placeName()));
      case NOT_EVENT -> Optional.of(new ParsedEvent(false, null, null, null, null, null, null));
      case UNREADABLE -> Optional.empty();
    };
  }

  private static Fields fieldsOf(ParsedEvent e) {
    return new Fields(e.title(), e.category(), e.startDate(), e.endDate(), e.timeText(), e.placeName());
  }

  // 오늘 몫 1회를 잡음. 모자라면 false (한국 날짜가 바뀌면 0 부터)
  private synchronized boolean reserveCall() {
    LocalDate today = LocalDate.ofInstant(clock.instant(), KOREA);
    if (!today.equals(budgetDay)) {
      budgetDay = today;
      budgetUsed = 0;
    }
    if (budgetUsed >= dailyLimit) {
      if (budgetUsed == dailyLimit) log.warn("캡션 AI: 오늘 호출 상한({}회) 도달 → 남은 블록은 다음 날 다시", dailyLimit);
      budgetUsed = dailyLimit + 1; // 경고는 하루 한 번만
      return false;
    }
    budgetUsed++;
    log.info("캡션 AI 호출 (오늘 {}/{})", budgetUsed, dailyLimit);
    return true;
  }

  // 공백 차이는 같은 글로 봄. SHA-256 앞 32글자 (BlockKeys 와 같은 길이)
  static String hash(String text) {
    String norm = CaptionText.normalize(text).strip().replaceAll("\\s+", " ");
    try {
      byte[] d = MessageDigest.getInstance("SHA-256").digest(("ai|" + norm).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(d).substring(0, 32);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e); // 모든 JDK 에 있는 알고리즘
    }
  }

  // 하루 상한 도달 (이유 칸에 클래스 이름이 남음: "AI 호출 실패: LimitReachedException")
  public static class LimitReachedException extends RuntimeException {
    LimitReachedException(int limit) {
      super("캡션 AI 하루 호출 상한 " + limit + "회");
    }
  }
}
