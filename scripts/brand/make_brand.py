"""수원바이블 브랜드 SVG 만들기 (새 로고 B안, 11/13 — docs/design/2026-11-13-todo-additions.md "Q2")

  - 헤더 로고 = W4 워드마크 "SUWON ∩ BIBLE": Playfair Display SemiBold(600), 자간 .04em, 두 단어 사이 아치
  - 부제 "수원 문화지도": Pretendard Medium(500), 자간 .3em (화면 10px · 자간 3px)
  - 파비콘 · 앱 아이콘 = B2 메인 아이콘: 벽돌 원 + 성곽선 + "SUWON / BIBLE" Inter ExtraBold(800)
  - 도장 "수 / 원"(옛 로고)은 웹 로고에서 뺐고, 인트로 1안 성문 장식(#seal)에만 남김: Song Myung
글자는 글꼴 윤곽(path)으로 바꿔 넣는다 → 화면은 글꼴 파일 없이 그려짐 (CSP 그대로).
글자 배치(커닝)는 HarfBuzz 로 브라우저와 같게 맞춤. 라이선스 · 출처: scripts/brand/README.md, scripts/brand/licenses/

사용: pip install fonttools brotli uharfbuzz
      python3 scripts/brand/make_brand.py <SongMyung-Regular.ttf> <PlayfairDisplay[wght].ttf> <Inter[opsz,wght].ttf>
      node scripts/brand/render.js            # PNG (아이콘 · 공유 이미지)
      python3 scripts/brand/make_ico.py       # favicon.ico
"""
import io
import sys
from pathlib import Path

import uharfbuzz as hb
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
    s = s[:-2] if s.endswith(".0") else s
    return "0" if s == "-0" else s


class RoundPen(SVGPathPen):
    """소수 1자리로 줄여 파일 크기를 줄임"""

    def __init__(self, glyphset):
        super().__init__(glyphset, ntos=fmt)


class Face:
    """고정 굵기 글꼴 하나 (fontTools = 윤곽, HarfBuzz = 배치)"""

    def __init__(self, ttfont):
        self.tt = ttfont
        ttfont.flavor = None  # woff2 → 일반 sfnt 로 (HarfBuzz 는 woff2 를 못 읽음)
        buf = io.BytesIO()
        ttfont.save(buf)
        self.hb = hb.Font(hb.Face(buf.getvalue()))
        self.upem = ttfont["head"].unitsPerEm
        self.gs = ttfont.getGlyphSet()
        self.order = ttfont.getGlyphOrder()

    def glyph_path(self, name, k, dx, dy):
        pen = RoundPen(self.gs)
        self.gs[name].draw(TransformPen(pen, (k, 0, 0, -k, dx, dy)))
        return pen.getCommands()

    def glyph_bounds(self, name):
        bp = BoundsPen(self.gs)
        self.gs[name].draw(bp)
        return bp.bounds


def text_paths(face, text, size, tracking=0.0, x=0.0, baseline=0.0):
    """글자줄을 path 로 (브라우저처럼: 커닝 + 글자마다 뒤에 자간). 반환: (d, 윤곽 x0, x1, 위, 아래, 펜 끝 x)
    tracking = em 단위 자간"""
    buf = hb.Buffer()
    buf.add_str(text)
    buf.guess_segment_properties()
    hb.shape(face.hb, buf, {"kern": True, "liga": False})
    k = size / face.upem
    ds, x0, x1, top, bottom = [], 1e9, -1e9, 1e9, -1e9
    for info, pos in zip(buf.glyph_infos, buf.glyph_positions):
        name = face.order[info.codepoint]
        gx, gy = x + pos.x_offset * k, baseline - pos.y_offset * k
        b = face.glyph_bounds(name)
        if b:
            xmin, ymin, xmax, ymax = b
            ds.append(face.glyph_path(name, k, gx, gy))
            x0, x1 = min(x0, gx + xmin * k), max(x1, gx + xmax * k)
            top, bottom = min(top, gy - ymax * k), max(bottom, gy - ymin * k)
        x += pos.x_advance * k + tracking * size
    return "".join(ds), x0, x1, top, bottom, x


# ---------- W4 워드마크 (font-size 100 단위) ----------
# 아치: viewBox 0 0 30 36 의 반원 지붕 + 직선 기둥 = 폭 .30em · 높이 .36em → font-size 100 에서 그대로 1배
ARCH = "M0 36V15A15 15 0 0 1 30 15V36Z"
ARCH_MARGIN = 0.32  # 아치 좌우 여백 (em)
TRACK = 0.04  # 자간 (em)


def wordmark(pf):
    """반환: (글자 d, 아치 d, viewBox(x, y, w, h)) — 아치 아래 = 글자 기준선 0"""
    d1, a0, a1, t1, b1, end1 = text_paths(pf, "SUWON", 100, TRACK)
    arch_x = end1 + ARCH_MARGIN * 100  # 마지막 글자 뒤 자간 + 여백
    d2, c0, c1, t2, b2, _ = text_paths(pf, "BIBLE", 100, TRACK, x=arch_x + 30 + ARCH_MARGIN * 100)
    arch = f"M{fmt(arch_x)} 0V-21A15 15 0 0 1 {fmt(arch_x + 30)} -21V0Z"
    x0, x1 = min(a0, c0), max(a1, c1)
    top, bot = min(t1, t2, -36), max(b1, b2, 0)
    return d1 + d2, arch, (x0, top, x1 - x0, bot - top)


# ---------- B2 메인 아이콘 (viewBox 0 0 200 200) ----------
CASTLE = "M28 78H44V52H60V78H76V52H92V78H108V52H124V78H140V52H156V78H172"


def b2_inner(inter, color=PAPER):
    """원 안 내용: 성곽선 + SUWON(기준선 128) · BIBLE(160), 27px · 자간 5 · 윤곽 가운데"""
    out = f'<path fill="none" stroke="{color}" stroke-width="9" d="{CASTLE}"/>'
    for word, y in (("SUWON", 128), ("BIBLE", 160)):
        _, x0, x1, *_ = text_paths(inter, word, 27, 5 / 27, baseline=y)
        d, *_ = text_paths(inter, word, 27, 5 / 27, x=100 - (x0 + x1) / 2, baseline=y)
        out += f'<path fill="{color}" d="{d}"/>'
    return out


def b2_icon(inter):
    return f'<circle cx="100" cy="100" r="100" fill="{BRICK}"/>' + b2_inner(inter)


def b2_app_icon(inter):
    """앱 아이콘: 바탕을 꽉 채운 벽돌색(가장자리를 잘라도 됨, maskable) + 원 안 내용을 .9배로 안전 영역(반지름 80%) 안에"""
    return f'<rect width="200" height="200" fill="{BRICK}"/><g transform="translate(10 10) scale(.9)">{b2_inner(inter)}</g>'


# ---------- 도장 (viewBox 0 0 100 100) — 인트로 1안 성문 장식(#seal)에만 씀 ----------
SEAL_OUTER = (
    "M4.6 3.1C30 2.4 70 3.6 95.8 2.7C97.2 2.7 97.5 3.4 97.4 4.6"
    "C96.8 30 97.6 70 97.1 95.5C97.1 96.9 96.4 97.4 95.2 97.3"
    "C70 96.6 30 97.5 4.3 96.9C3.1 96.9 2.6 96.2 2.7 95"
    "C3.3 70 2.5 30 3 4.5C3 3.4 3.5 3.1 4.6 3.1Z"
)
SEAL_FRAME = "M91.4 67V8.6H8.6V91.4H73"
PIN = (
    "M84 93.6C81.2 89.4 77.6 86.2 77.6 81.4A6.4 6.4 0 1 1 90.4 81.4"
    "C90.4 86.2 86.8 89.4 84 93.6ZM84 78.7A2.7 2.7 0 1 0 84 84.1A2.7 2.7 0 1 0 84 78.7Z"
)
SEAL_GLYPH_BOX = {"수": (18, 15, 70, 48.5), "원": (18, 52.5, 70, 86.5)}


def fit(face, ch, box):
    """글자의 실제 윤곽을 box(x0, y0, x1, y1) 에 꽉 맞춤 (가로 · 세로 따로 늘림 = 도장 새김처럼)"""
    name = face.tt.getBestCmap()[ord(ch)]
    xmin, ymin, xmax, ymax = face.glyph_bounds(name)
    x0, y0, x1, y1 = box
    sx, sy = (x1 - x0) / (xmax - xmin), (y1 - y0) / (ymax - ymin)
    pen = RoundPen(face.gs)
    face.gs[name].draw(TransformPen(pen, (sx, 0, 0, -sy, x0 - xmin * sx, y0 + ymax * sy)))
    return pen.getCommands()


def seal_inner(song, white="#fff", frame=2.2, weight=1.1):
    d = "".join(fit(song, ch, box) for ch, box in SEAL_GLYPH_BOX.items())
    return (
        f'<path fill="{BRICK}" d="{SEAL_OUTER}"/>'
        f'<path fill="none" stroke="{white}" stroke-width="{frame}" stroke-linejoin="round" d="{SEAL_FRAME}"/>'
        f'<path fill="{white}" stroke="{white}" stroke-width="{weight}" stroke-linejoin="round" d="{d}"/>'
        f'<path fill="{white}" fill-rule="evenodd" d="{PIN}"/>'
    )


def credit(fonts):
    """SVG 주석: 그 파일에 실제로 쓴 글꼴만 적음"""
    return f"<!-- 수원바이블 로고. 글자 윤곽: {fonts} (SIL Open Font License 1.1), 만든 방법 scripts/brand -->"


SPRITE_CREDIT = credit("Playfair Display · Pretendard · Song Myung")  # logo.svg: 워드마크 · 부제 · 도장
ICON_CREDIT = credit("Inter")  # B2 아이콘 (favicon.svg · 앱 아이콘)


def svg(viewbox, body, w=None, h=None):
    size = f' width="{w}" height="{h}"' if w else ""
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{viewbox}"{size}>{ICON_CREDIT}{body}</svg>\n'


def vb(box):
    return " ".join(fmt(v) for v in box)


def main():
    song = Face(TTFont(sys.argv[1]))
    pf = Face(instancer.instantiateVariableFont(TTFont(sys.argv[2]), {"wght": 600}))
    inter = Face(instancer.instantiateVariableFont(TTFont(sys.argv[3]), {"wght": 800, "opsz": 27}))
    pre_med = Face(TTFont(BRAND / "fonts/Pretendard-Medium.subset.woff2"))

    word_d, arch_d, word_box = wordmark(pf)
    # 부제: 화면 10px · 자간 3px = .3em. 윤곽 상자로 자름 → 워드마크와 윤곽끼리 가운데 맞춤 (CSS align-items: center)
    tag_d, t0, t1, ttop, tbot, _ = text_paths(pre_med, "수원 문화지도", 100, 0.3)
    tag_box = (t0, ttop, t1 - t0, tbot - ttop)

    WORK.mkdir(exist_ok=True)
    # 화면용 스프라이트: <svg><use href="/brand/logo.svg#…"/></svg>
    #   #wordmark: 글자 = CSS color(currentColor), 아치 = fill 속성을 비워 CSS fill 을 물려받음 (밝음 · 어두움 따로)
    sprite = (
        f'<svg xmlns="http://www.w3.org/2000/svg">{SPRITE_CREDIT}'
        f'<symbol id="wordmark" viewBox="{vb(word_box)}"><path fill="currentColor" d="{word_d}"/><path d="{arch_d}"/></symbol>'
        f'<symbol id="tagline" viewBox="{vb(tag_box)}"><path fill="currentColor" d="{tag_d}"/></symbol>'
        f'<symbol id="seal" viewBox="0 0 100 100">{seal_inner(song)}</symbol>'
        "</svg>\n"
    )
    (BRAND / "logo.svg").write_text(sprite, encoding="utf-8")
    (BRAND / "favicon.svg").write_text(svg("0 0 200 200", b2_icon(inter), 32, 32), encoding="utf-8")
    (WORK / "icon.svg").write_text(svg("0 0 200 200", b2_app_icon(inter), 512, 512), encoding="utf-8")
    (WORK / "favicon-16.svg").write_text(svg("0 0 200 200", b2_icon(inter), 16, 16), encoding="utf-8")

    # 공유 이미지 1200x630 (render.js 가 찍음) — 문서 Q2 "공유 미리보기 배너"
    word_px = 80 / 100  # 워드마크 글자 80px
    ww, wh = word_box[2] * word_px, word_box[3] * word_px
    fonts = "".join(
        f"@font-face{{font-family:Pretendard;font-weight:{w};src:url({(BRAND / f'fonts/Pretendard-{n}.subset.woff2').as_uri()})}}"
        for w, n in ((500, "Medium"), (700, "Bold"))
    )
    og = f"""<!doctype html><meta charset="utf-8"><style>{fonts}
body{{margin:0}}.og{{position:relative;width:1200px;height:630px;background:{PAPER};overflow:hidden;font-family:Pretendard}}
.og>*{{position:absolute;margin:0;line-height:1;white-space:nowrap}}
</style><body><div class="og">
<div style="left:88px;top:0;width:2px;height:630px;background:#e4dccd"></div>
<svg style="left:120px;top:196px" width="{fmt(ww)}" height="{fmt(wh)}" viewBox="{vb(word_box)}"><path fill="{INK}" d="{word_d}"/><path fill="{BRICK}" d="{arch_d}"/></svg>
<p style="left:120px;top:{fmt(196 + wh + 22)}px;font-size:26px;font-weight:700;letter-spacing:10px;color:{BRICK}">수원 문화지도</p>
<div style="left:120px;top:418px;width:110px;height:2px;background:{INK}"></div>
<p style="left:120px;top:440px;font-size:30px;font-weight:500;color:#4a4540">수원의 공연 · 전시 · 축제를 지도에서 한눈에</p>
<p style="left:120px;top:498px;font-size:22px;font-weight:500;color:#8a8178">suwonbible.com</p>
<svg style="left:860px;top:120px" width="230" height="230" viewBox="0 0 200 200">{b2_icon(inter)}</svg>
</div></body>
"""
    (WORK / "og.html").write_text(og, encoding="utf-8")

    # CSS 에 넣을 크기 (글자 크기 px → 윤곽 상자 px)
    print(f"sprite bytes {len(sprite)}")
    print(f"wordmark box(em) w {word_box[2] / 100:.4f} h {word_box[3] / 100:.4f} (가로/세로 {word_box[2] / word_box[3]:.3f})")
    print(f"tagline  box(em) w {tag_box[2] / 100:.4f} h {tag_box[3] / 100:.4f} (가로/세로 {tag_box[2] / tag_box[3]:.3f})")
    for fs in (24, 21, 32):
        print(f"  워드마크 {fs}px → {word_box[2] * fs / 100:.1f} x {word_box[3] * fs / 100:.1f}")
    for fs in (10, 14):
        print(f"  부제 {fs}px → {tag_box[2] * fs / 100:.1f} x {tag_box[3] * fs / 100:.1f}")


if __name__ == "__main__":
    main()
