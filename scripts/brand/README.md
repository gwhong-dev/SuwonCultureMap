# 브랜드 이미지 만들기 (scripts/brand)

수원바이블 로고 = **새 로고 B안** (11/13, 사용자 로고 파일 "수원바이블 로고 파일" 1차 최종 11/12 — 확정 값 `docs/design/2026-11-13-todo-additions.md` "Q2").
사용 규칙: **웹 헤더 = W4 워드마크 + 부제 "수원 문화지도"(아이콘 없음), 웹 파비콘 · 앱 아이콘 = B2 메인 아이콘**. 옛 도장 "수 / 원"은 인트로 1안 성문 장식에만 남김.
글자는 글꼴 **윤곽(path)** 으로 바꿔 SVG 에 넣음 → 화면은 글꼴 파일 없이 그려짐 (웹 글꼴을 따로 불러오지 않음 · CSP 그대로). 글자 배치(커닝)는 HarfBuzz 로 브라우저와 같게 맞춤.

| 쓰는 곳 | 글꼴 | 라이선스 · 출처 |
|---|---|---|
| W4 워드마크 `SUWON ∩ BIBLE` (600, 자간 .04em) | Playfair Display (가변 글꼴 → 굵기 고정) | SIL OFL 1.1 — `licenses/PlayfairDisplay-OFL.txt`, github.com/google/fonts `ofl/playfairdisplay` |
| B2 아이콘 글자 `SUWON` / `BIBLE` (800, 27px · 자간 5) | Inter (가변 글꼴 → 굵기 800 · opsz 27 고정) | SIL OFL 1.1 — `licenses/Inter-OFL.txt`, github.com/google/fonts `ofl/inter` |
| 부제 `수원 문화지도` (500, 자간 .3em) · 공유 이미지 글 | Pretendard (저장소 `static/brand/fonts` 의 subset) | SIL OFL 1.1 — `static/brand/fonts/OFL-LICENSE.txt` |
| 도장 "수 / 원" (인트로 1안만) | Song Myung Regular | SIL OFL 1.1 — `licenses/SongMyung-OFL.txt`, github.com/google/fonts `ofl/songmyung` |

글꼴 파일 자체는 저장소 · 서버에 넣지 않음 (Pretendard subset 은 본문 글꼴이라 원래 있음). SVG 안 주석에 글꼴 이름 · 라이선스를 적어 둠.

## 만드는 파일 (`src/main/resources/static/brand`)

| 파일 | 내용 |
|---|---|
| `logo.svg` | 화면용 묶음: `#wordmark` · `#tagline` · `#seal` (`<svg><use href="/brand/logo.svg#wordmark"/></svg>`) |
| `favicon.svg` · `favicon-32.png` | B2 아이콘 (벽돌 원 + 성곽선 + 글자). 32px 에선 글자가 거의 안 보여도 원 + 성곽선으로 알아봄 |
| `../favicon.ico` | B2 아이콘 16px + 32px |
| `apple-touch-icon.png` (180) · `icon-192.png` · `icon-512.png` | 벽돌색 꽉 찬 바탕 + B2 원 안 내용 0.9배 (maskable 안전 영역 = 반지름 80% 안) |
| `og-image.png` (1200×630) | 종이 바탕 · 왼쪽 세로선 · W4 워드마크 80px + 부제 · 설명 · 주소, 오른쪽 B2 아이콘 230px |

### `logo.svg` 기호와 크기

- 기호는 **글자 윤곽 상자로 잘려** 있음 → CSS 에서 가로 · 세로를 글자 크기 × 비율로 줌 (`make_brand.py` 가 실행 끝에 값을 찍음)
  - `#wordmark`: 가로 = 글자 크기 × 7.767, 세로 × 0.736 (24px → 186.4 × 17.7, 21px → 163.1 × 15.5)
  - `#tagline`: 가로 = 글자 크기 × 7.166, 세로 × 0.875 (10px → 71.7 × 8.8)
- 색: 워드마크 글자 = CSS `color`(currentColor), 아치 = `fill` 속성을 비워 CSS `fill` 을 물려받음 → `.brand-name { color: var(--text); fill: var(--brand-sub); }` 로 밝음 · 어두움 따로

## 다시 만들기

```bash
pip install fonttools brotli uharfbuzz pillow
npm i playwright && npx playwright install chromium   # render.js 용
# 글꼴 받기 (임시 폴더에)
curl -LO https://raw.githubusercontent.com/google/fonts/main/ofl/songmyung/SongMyung-Regular.ttf
curl -L -o PlayfairDisplay.ttf "https://raw.githubusercontent.com/google/fonts/main/ofl/playfairdisplay/PlayfairDisplay%5Bwght%5D.ttf"
curl -L -o Inter.ttf "https://raw.githubusercontent.com/google/fonts/main/ofl/inter/Inter%5Bopsz,wght%5D.ttf"
python3 scripts/brand/make_brand.py SongMyung-Regular.ttf PlayfairDisplay.ttf Inter.ttf   # SVG + 중간 파일(scripts/brand/out)
node scripts/brand/render.js                                                              # PNG (playwright)
python3 scripts/brand/make_ico.py                                                         # favicon.ico
```

- Windows 에서 `render.js` 가 `browserType.launch: spawn UNKNOWN` 으로 멈추면(playwright 내장 크로미움 실행 실패) `BRAND_CHROME=1 node scripts/brand/render.js` 로 설치된 크롬으로 찍는다.
- 다시 찍으면 그리는 엔진 차이로 내용이 같은 PNG 도 바이트가 달라질 수 있음 → 내용이 바뀐 파일만 커밋.
