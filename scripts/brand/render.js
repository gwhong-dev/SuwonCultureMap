// 브랜드 PNG 만들기: make_brand.py 가 만든 SVG/HTML 을 크로미움으로 찍음
// 사용: node scripts/brand/render.js   (playwright 필요)
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "../..");
const brand = path.join(root, "src/main/resources/static/brand");
const out = path.join(__dirname, "out");

async function shot(page, svgFile, size, dest) {
  const svg = fs.readFileSync(svgFile, "utf8");
  await page.setViewportSize({ width: size, height: size });
  await page.setContent(`<body style="margin:0">${svg.replace(/width="\d+" height="\d+"/, `width="${size}" height="${size}"`)}</body>`);
  await page.screenshot({ path: dest, omitBackground: true, clip: { x: 0, y: 0, width: size, height: size } });
}

(async () => {
  // 내장 크로미움이 안 뜨는 PC(Windows spawn UNKNOWN)는 설치된 크롬으로: BRAND_CHROME=1 node scripts/brand/render.js
  const browser = await chromium.launch(process.env.BRAND_CHROME ? { channel: "chrome" } : {});
  const page = await browser.newPage({ deviceScaleFactor: 1 });
  await shot(page, path.join(out, "icon.svg"), 180, path.join(brand, "apple-touch-icon.png"));
  await shot(page, path.join(out, "icon.svg"), 192, path.join(brand, "icon-192.png"));
  await shot(page, path.join(out, "icon.svg"), 512, path.join(brand, "icon-512.png"));
  await shot(page, path.join(brand, "favicon.svg"), 32, path.join(brand, "favicon-32.png"));
  await shot(page, path.join(out, "favicon-16.svg"), 16, path.join(out, "favicon-16.png"));
  await page.setViewportSize({ width: 1200, height: 630 });
  await page.goto("file://" + path.join(out, "og.html"));
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: path.join(brand, "og-image.png"), clip: { x: 0, y: 0, width: 1200, height: 630 } });
  await browser.close();
})();
