# 브랜드 이미지 만들기 (scripts/brand)

수원바이블 로고 = **낙관(도장) "수 / 원" + 지도 핀** · 워드마크 "수원바이블" · 아래 줄 부제 "수원 문화지도" (11/11, #201 다시 만듦).
글자는 글꼴 **윤곽(path)** 으로 바꿔 SVG 에 넣음 → 화면은 글꼴 파일 없이 그려짐 (본문 글꼴은 Pretendard 그대로).

| 쓰는 곳 | 글꼴 | 라이선스 · 출처 |
|---|---|---|
| 도장 "수 / 원" | Song Myung Regular | SIL OFL 1.1 — `licenses/SongMyung-OFL.txt`, github.com/google/fonts `ofl/songmyung` |
| 워드마크 (800) · 부제 (600) | Hahmlet (가변 글꼴 → 굵기 고정) | SIL OFL 1.1 — `licenses/Hahmlet-OFL.txt`, github.com/google/fonts `ofl/hahmlet` |

글꼴 파일 자체는 저장소 · 서버에 넣지 않음 (윤곽만 씀). SVG 안 주석에 글꼴 이름 · 라이선스를 적어 둠.

## 만드는 파일 (`src/main/resources/static/brand`)

| 파일 | 내용 |
|---|---|
| `logo.svg` | 화면용 묶음: `#seal` · `#wordmark` · `#tagline` (`<svg><use href="/brand/logo.svg#seal"/></svg>`, 글자 색은 CSS `color`) |
| `logo-mark.svg` · `logo-lockup.svg` | 도장만 · 도장 + 워드마크 가로 로고 (문서용) |
| `favicon.svg` · `favicon-32.png` | 도장 (32px 용으로 선 · 글자 조금 굵게) |
| `../favicon.ico` | 16px = "수" 한 글자 + 핀 (단순화), 32px = 도장 |
| `apple-touch-icon.png` (180) · `icon-192.png` · `icon-512.png` | 벽돌색 꽉 찬 바탕 + 가운데 도장 (maskable 안전 영역 안) |
| `og-image.png` (1200×630) | 종이 바탕 · 왼쪽 글 덩어리 · 오른쪽 기울인 도장 |

부제 위치(11/11 시안 d): `logo-lockup.svg` · `og-image.png` 는 부제 첫 글자 윤곽의 왼쪽 끝을 워드마크 첫 글자 '수' 윤곽의 가로 중심(워드마크 높이 × 0.515)에 맞춤 — 스크립트가 글꼴 윤곽으로 계산하고 실행 끝에 두 값을 찍음.
화면용 `logo.svg#tagline` 은 그대로 두고 화면에서 CSS `margin-left`(워드마크 높이 × 0.5)로 맞춤 (#216).

## 다시 만들기

```bash
pip install fonttools pillow
npm i playwright && npx playwright install chromium   # render.js 용
# 글꼴 받기 (임시 폴더에)
curl -LO https://raw.githubusercontent.com/google/fonts/main/ofl/songmyung/SongMyung-Regular.ttf
curl -L -o Hahmlet.ttf "https://raw.githubusercontent.com/google/fonts/main/ofl/hahmlet/Hahmlet%5Bwght%5D.ttf"
python3 scripts/brand/make_brand.py SongMyung-Regular.ttf Hahmlet.ttf   # SVG + 중간 파일(scripts/brand/out)
node scripts/brand/render.js                                             # PNG (playwright)
python3 scripts/brand/make_ico.py                                        # favicon.ico
```

- Windows 에서 `render.js` 가 `browserType.launch: spawn UNKNOWN` 으로 멈추면(11/12 작업 PC, playwright 내장 크로미움 실행 실패) `chromium.launch({ channel: "chrome" })` 처럼 설치된 크롬으로 찍는다.
- 다시 찍으면 그리는 엔진 차이로 내용이 같은 PNG 도 바이트가 달라질 수 있음 → 내용이 바뀐 파일만 커밋.
