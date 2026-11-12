package com.suwon.festival.service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/*
랜덤 닉네임 = 형용사 30개 × 수원 랜드마크 30개 = 900가지 (11/13 사용자 결정, docs/design/2026-11-13-improvement-drafts.md P5)
- 조합은 "형용사 랜드마크" (띄어쓰기 하나). 길이는 최대 10자 → 겹칠 때 붙이는 숫자 2자리를 더해도 12자 안
- 겹치면 다른 조합을 몇 번 더 뽑고, 그래도 겹치면 끝에 숫자 2자리(10~99)
- 사용자가 직접 고치는 이름의 규칙(2~12자, 한글 · 영문 · 숫자 · 띄어쓰기)도 여기 한 곳에
*/
@Component
public class NicknameGenerator {

    static final List<String> ADJECTIVES = List.of(
            "푸르른", "아늑한", "반짝이는", "포근한", "느긋한",
            "산뜻한", "다정한", "수줍은", "용감한", "졸린",
            "배고픈", "신나는", "조용한", "씩씩한", "해맑은",
            "몽글몽글", "노을빛", "달빛", "별빛", "단풍빛",
            "벚꽃빛", "은행빛", "새벽의", "한낮의", "느린",
            "동글동글", "말랑한", "상냥한", "든든한", "설레는");

    static final List<String> LANDMARKS = List.of(
            "화성행궁", "장안문", "팔달문", "화서문", "창룡문",
            "방화수류정", "화홍문", "서장대", "동장대", "연무대",
            "서북공심돈", "봉돈", "팔달산", "광교산", "광교호수",
            "만석공원", "서호", "일월저수지", "수원천", "행궁동",
            "지동시장", "못골시장", "통닭거리", "수원역", "효원공원",
            "월화원", "수원박물관", "화성어차", "행리단길", "남문시장");

    static final int MIN_LENGTH = 2;
    static final int MAX_LENGTH = 12;
    private static final Pattern ALLOWED = Pattern.compile("^[가-힣a-zA-Z0-9 ]+$");
    private static final int COMBO_TRIES = 20;
    private static final int SUFFIX_TRIES = 50;

    private final Random random;

    public NicknameGenerator() {
        this(new SecureRandom());
    }

    NicknameGenerator(Random random) {
        this.random = random;
    }

    // taken: 이미 쓰는 이름인지 (DB 확인). 겹치지 않는 이름을 돌려줌
    public String generate(Predicate<String> taken) {
        for (int i = 0; i < COMBO_TRIES; i++) {
            String name = randomCombo();
            if (!taken.test(name)) return name;
        }
        String base = randomCombo();
        for (int i = 0; i < SUFFIX_TRIES; i++) {
            String name = base + (10 + random.nextInt(90));
            if (!taken.test(name)) return name;
        }
        throw new IllegalStateException("닉네임을 만들지 못했어요.");
    }

    String randomCombo() {
        return ADJECTIVES.get(random.nextInt(ADJECTIVES.size())) + " "
                + LANDMARKS.get(random.nextInt(LANDMARKS.size()));
    }

    // 사용자가 입력한 이름 정리: 앞뒤 공백 제거 + 가운데 연속 공백은 하나로
    public static String normalize(String input) {
        return input == null ? "" : input.strip().replaceAll("\\s+", " ");
    }

    // 규칙에 안 맞으면 안내 문구, 맞으면 null
    public static String validate(String normalized) {
        if (normalized.length() < MIN_LENGTH || normalized.length() > MAX_LENGTH) {
            return "닉네임은 " + MIN_LENGTH + "~" + MAX_LENGTH + "자로 정해 주세요.";
        }
        if (!ALLOWED.matcher(normalized).matches()) {
            return "닉네임은 한글 · 영문 · 숫자 · 띄어쓰기만 쓸 수 있어요.";
        }
        return null;
    }
}
