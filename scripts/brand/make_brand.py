"""수원바이블 브랜드 SVG 만들기 (낙관(도장) + 핀, 11/11)

글자는 글꼴 윤곽(path)으로 바꿔 넣는다 → 화면은 글꼴 파일 없이 그려짐.
  - 도장 "수 / 원": Song Myung (SIL OFL 1.1)
  - 워드마크 "수원바이블": Hahmlet 800, 부제 "수원 문화지도": Hahmlet 600 (SIL OFL 1.1)
  라이선스 · 출처: scripts/brand/README.md, scripts/brand/licenses/

사용: pip install fonttools
      python3 scripts/brand/make_brand.py <SongMyung-Regular.ttf> <Hahmlet[wght].ttf>
      node scripts/brand/render.js            # PNG (아이콘 · 공유 이미지 · ico)
"""
import sys
from pathlib import Path

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = Path(__file__).resolve().parents[2]
BRAND = ROOT / "src/main/resources/static/brand"
WORK = Path(__file__).resolve().parent / "out"  # 렌더용 중간 파일 (gitignore)

BRICK = "#b4472a"
INK = "#1c1b1a"
PAPER = "#f6f1e7"


def fmt(v):
    s = f"{v:.1f}"
    return s[:-2] if s.endswith(".0") else s


class RoundPen(SVGPathPen):
    """소수 1자리로 줄여 파일 크기를 줄임"""

    def __init__(self, glyphset):
        super().__init__(glyphset, ntos=fmt)


def glyph_path(font, ch, sx, sy, dx, dy):
    """글자 하나를 (x' = sx*x + dx, y' = -sy*y + dy) 로 옮긴 SVG path d"""
    gs = font.getGlyphSet()
    name = font.getBestCmap()[ord(ch)]
    pen = RoundPen(gs)
    gs[name].draw(TransformPen(pen, (sx, 0, 0, -sy, dx, dy)))
    return pen.getCommands()


def glyph_bounds(font, ch):
    gs = font.getGlyphSet()
    name = font.getBestCmap()[ord(ch)]
    bp = BoundsPen(gs)
    gs[name].draw(bp)
    return bp.bounds, gs[name].width


def fit(font, ch, box):
    """글자의 실제 윤곽을 box(x0, y0, x1, y1) 에 꽉 맞춤 (가로 · 세로 따로 늘림 = 도장 새김처럼)"""
    (xmin, ymin, xmax, ymax), _ = glyph_bounds(font, ch)
    x0, y0, x1, y1 = box
    sx = (x1 - x0) / (xmax - xmin)
    sy = (y1 - y0) / (ymax - ymin)
    return glyph_path(font, ch, sx, sy, x0 - xmin * sx, y0 + ymax * sy)


def text_paths(font, text, size, tracking=0.0, x=0.0, baseline=0.0):
    """글자줄을 path 로. 반환: (d 목록, 끝 x, 윤곽 위 · 아래)"""
    upem = font["head"].unitsPerEm
    k = size / upem
    ds, top, bottom = [], 1e9, -1e9
    for ch in text:
        if ch == " ":  # 띄어쓰기 = 자간에 .28em 더함 (넓은 자간에서도 낱말이 나뉘어 보이게)
            x += 0.28 * size
            continue
        (xmin, ymin, xmax, ymax), adv = glyph_bounds(font, ch)
        ds.append(glyph_path(font, ch, k, k, x, baseline))
        top, bottom = min(top, baseline - ymax * k), max(bottom, baseline - ymin * k)
        x += adv * k + tracking * size
    return ds, x - tracking * size, top, bottom


# ---------- 도장 (viewBox 0 0 100 100) ----------
# 바깥: 모서리 · 변이 아주 조금 고르지 않음 (손으로 찍은 도장 느낌, 값은 고정 → 매번 같은 모양)
SEAL_OUTER = (
    "M4.6 3.1C30 2.4 70 3.6 95.8 2.7C97.2 2.7 97.5 3.4 97.4 4.6"
    "C96.8 30 97.6 70 97.1 95.5C97.1 96.9 96.4 97.4 95.2 97.3"
    "C70 96.6 30 97.5 4.3 96.9C3.1 96.9 2.6 96.2 2.7 95"
    "C3.3 70 2.5 30 3 4.5C3 3.4 3.5 3.1 4.6 3.1Z"
)
# 안쪽 흰 테두리 선: 오른쪽 아래 모서리는 비워 핀이 들어감 (지도에 찍은 위치 표시처럼)
SEAL_FRAME = "M91.4 67V8.6H8.6V91.4H73"
# 핀 (머리 원 + 구멍, 끝이 아래). 머리 중심 (84, 80)
PIN = (
    "M84 93.6C81.2 89.4 77.6 86.2 77.6 81.4A6.4 6.4 0 1 1 90.4 81.4"
    "C90.4 86.2 86.8 89.4 84 93.6ZM84 78.7A2.7 2.7 0 1 0 84 84.1A2.7 2.7 0 1 0 84 78.7Z"
)
SEAL_GLYPH_BOX = {"수": (18, 15, 70, 48.5), "원": (18, 52.5, 70, 86.5)}


def seal_inner(song, white="#fff", frame=2.2, weight=1.1):
    """weight = 글자 윤곽에 더하는 흰 선 굵기 (Song Myung 이 가늘어 새김처럼 조금 두껍게)"""
    d = "".join(fit(song, ch, box) for ch, box in SEAL_GLYPH_BOX.items())
    return (
        f'<path fill="{BRICK}" d="{SEAL_OUTER}"/>'
        f'<path fill="none" stroke="{white}" stroke-width="{frame}" stroke-linejoin="round" d="{SEAL_FRAME}"/>'
        f'<path fill="{white}" stroke="{white}" stroke-width="{weight}" stroke-linejoin="round" d="{d}"/>'
        f'<path fill="{white}" fill-rule="evenodd" d="{PIN}"/>'
    )


def seal_small(song):
    """16px 파비콘: "수" 한 글자 + 핀 점. 테두리 선 없음 (16px 에선 뭉개짐)"""
    d = fit(song, "수", (12, 12, 70, 88))
    return (
        f'<rect width="100" height="100" rx="6" fill="{BRICK}"/>'
        f'<path fill="#fff" stroke="#fff" stroke-width="5" stroke-linejoin="round" d="{d}"/>'
        f'<path fill="#fff" d="M84 94C80 88 75 84 75 77.5A9 9 0 1 1 93 77.5C93 84 88 88 84 94Z"/>'
    )


def seal_icon(song):
    """앱 아이콘: 바탕을 꽉 채운 벽돌색(가장자리를 잘라도 됨) + 안쪽 도장. 중앙 80% 안에 글자 · 핀"""
    return (
        f'<rect width="100" height="100" fill="{BRICK}"/>'
        f'<g transform="translate(12 12) scale(.76)">{seal_inner(song)}</g>'
    )


CREDIT = "<!-- 수원바이블 로고. 글자 윤곽: Song Myung · Hahmlet (SIL Open Font License 1.1), 만든 방법 scripts/brand -->"


def svg(viewbox, body, w=None, h=None, extra=""):
    size = f' width="{w}" height="{h}"' if w else ""
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{viewbox}"{size}{extra}>{CREDIT}{body}</svg>\n'


def main():
    song = TTFont(sys.argv[1])
    var = TTFont(sys.argv[2])
    ham800 = instancer.instantiateVariableFont(TTFont(sys.argv[2]), {"wght": 800})
    ham600 = instancer.instantiateVariableFont(var, {"wght": 600})

    # ---------- 워드마크: "수원바이블" 위 줄, 부제 아래 줄 (자간 .32em) ----------
    name_ds, name_w, name_top, name_bot = text_paths(ham800, "수원바이블", 100, tracking=-0.02, baseline=100)
    sub_size = 30
    sub_ds, sub_w, sub_top, sub_bot = text_paths(ham600, "수원 문화지도", sub_size, tracking=0.32, baseline=0)
    # 부제는 워드마크 왼쪽 끝에 맞추고, 워드마크 아래 26 띄움
    sub_y = name_bot + 26 - sub_top
    sub_ds, sub_w, sub_top2, sub_bot2 = text_paths(ham600, "수원 문화지도", sub_size, tracking=0.32, baseline=sub_y, x=4)
    top = name_top
    vb_w, vb_h = max(name_w, sub_w + 4), sub_bot2 - top
    name_d, sub_d = "".join(name_ds), "".join(sub_ds)

    WORK.mkdir(exist_ok=True)
    # 화면용 스프라이트: <svg><use href="/brand/logo.svg#…"/></svg> 로 씀. 색은 CSS(currentColor) 로
    word_vb = f"0 {fmt(top)} {fmt(vb_w)} {fmt(vb_h)}"
    sprite = (
        f'<svg xmlns="http://www.w3.org/2000/svg">{CREDIT}'
        f'<symbol id="seal" viewBox="0 0 100 100">{seal_inner(song, frame=2.6, weight=1.8)}</symbol>'  # 화면은 32~36px → 선 · 글자 조금 굵게
        f'<symbol id="wordmark" viewBox="0 {fmt(top)} {fmt(name_w)} {fmt(name_bot - top)}"><path fill="currentColor" d="{name_d}"/></symbol>'
        f'<symbol id="tagline" viewBox="4 {fmt(sub_top2)} {fmt(sub_w)} {fmt(sub_bot2 - sub_top2)}"><path fill="currentColor" d="{sub_d}"/></symbol>'
        "</svg>\n"
    )
    (BRAND / "logo.svg").write_text(sprite, encoding="utf-8")
    (BRAND / "logo-mark.svg").write_text(svg("0 0 100 100", seal_inner(song), 32, 32), encoding="utf-8")
    (BRAND / "favicon.svg").write_text(svg("0 0 100 100", seal_inner(song, frame=3.2, weight=2.6), 32, 32), encoding="utf-8")
    (WORK / "favicon-16.svg").write_text(svg("0 0 100 100", seal_small(song), 16, 16), encoding="utf-8")
    (WORK / "icon.svg").write_text(svg("0 0 100 100", seal_icon(song), 512, 512), encoding="utf-8")
    (WORK / "wordmark.svg").write_text(
        svg(word_vb, f'<path fill="{INK}" d="{name_d}"/><path fill="{BRICK}" d="{sub_d}"/>')
    )
    # 문서 · 공유용 가로 로고 (도장 + 워드마크)
    gap = 0.18 * vb_h
    lock_w = vb_h + gap + vb_w
    lock = (
        f'<g transform="scale({fmt(vb_h / 100)})">{seal_inner(song)}</g>'
        f'<g transform="translate({fmt(vb_h + gap)} {fmt(-top)})"><path fill="{INK}" d="{name_d}"/><path fill="{BRICK}" d="{sub_d}"/></g>'
    )
    (BRAND / "logo-lockup.svg").write_text(svg(f"0 0 {fmt(lock_w)} {fmt(vb_h)}", lock), encoding="utf-8")

    # 공유 이미지 1200x630 (render.js 가 찍음). 종이 바탕 · 왼쪽 정렬 · 비대칭:
    #   왼쪽 = 워드마크 · 부제 · 설명 (글 덩어리), 오른쪽 위 = 크게 찍은 도장 (살짝 기울임). 가운데 정렬 · 아래 색 띠 없음
    word_h_px = 150
    word_w_px = word_h_px * vb_w / vb_h
    font = (BRAND / "fonts/Pretendard-Medium.subset.woff2").as_uri()
    og = f"""<!doctype html><meta charset="utf-8"><style>@font-face{{font-family:Pretendard;font-weight:500;src:url({font})}}body{{margin:0}}</style><body>
<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630" viewBox="0 0 1200 630">
<rect width="1200" height="630" fill="{PAPER}"/>
<path d="M88 0V630" stroke="{BRICK}" stroke-opacity=".22"/>
<svg x="120" y="196" width="{fmt(word_w_px)}" height="{word_h_px}" viewBox="{word_vb}"><path fill="{INK}" d="{name_d}"/><path fill="{BRICK}" d="{sub_d}"/></svg>
<path d="M122 420H232" stroke="{INK}" stroke-width="2"/>
<text x="120" y="472" font-family="Pretendard" font-size="32" font-weight="500" fill="#4a4540">수원의 공연 · 전시 · 축제를 지도에서 한눈에</text>
<text x="120" y="522" font-family="Pretendard" font-size="22" font-weight="500" fill="#8a8178" letter-spacing="1">suwonbible.com</text>
<g transform="translate(846 92) rotate(-4 120 120) scale(2.4)">{seal_inner(song)}</g>
</svg></body>
"""
    (WORK / "og.html").write_text(og, encoding="utf-8")
    print("wordmark ratio", vb_w / vb_h, "sprite bytes", len(sprite))


if __name__ == "__main__":
    main()
