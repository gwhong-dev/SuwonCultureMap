// 첫 화면 시험 4개 — 실제 브라우저에서 누르고 키를 쳐서 화면이 그대로 동작하는지 (준비 = support.js, 실행 = docs/e2e-test.md)
const { test, expect, EVENTS, openHome, openMobileMap } = require("./support");

const PC = { width: 1280, height: 800 };
const MOBILE = { width: 390, height: 844 }; // index.js mobileQuery = 1024px 이하

const TITLES = EVENTS.map((e) => e.title);

test.describe("PC", () => {
  test.use({ viewport: PC });

  test("☰ 메뉴: 열면 첫 줄로 초점, Esc · 바깥 누르기로 닫힘", async ({ page }) => {
    await openHome(page);
    const button = page.locator("#mobileMenuBtn");
    const links = page.locator("#menuLinks");

    await button.click();
    await expect(button).toHaveAttribute("aria-expanded", "true");
    await expect(links).toBeVisible();
    // 첫 보이는 줄로 초점 (로그인 전 로그아웃 · PC 에 없는 언어 줄은 건너뜀)
    await expect(page.locator("#menuLinks a[href='about.html']")).toBeFocused();

    await page.keyboard.press("Escape");
    await expect(button).toHaveAttribute("aria-expanded", "false");
    await expect(links).toBeHidden();
    await expect(button).toBeFocused();

    await button.click();
    await expect(links).toBeVisible();
    await page.locator("#pcDetail").click({ position: { x: 20, y: 20 } });
    await expect(button).toHaveAttribute("aria-expanded", "false");
    await expect(links).toBeHidden();
  });

  test("길찾기 작은 창: 단추 위에 열리고 × 로 닫으면 단추로 초점", async ({ page }) => {
    await openHome(page);
    const title = page.locator("#pcdTitle");
    await expect(title).toHaveText(new RegExp(TITLES.join("|"))); // 상세 칸 = 목록 첫 행사
    const shownTitle = (await title.textContent()).trim();
    const shown = EVENTS.find((e) => e.title === shownTitle);

    const route = page.locator("#pcdRoute");
    const pop = page.locator("#routePop");
    await expect(pop).toBeHidden();
    await route.click();
    await expect(pop).toBeVisible();
    await expect(route).toHaveAttribute("aria-expanded", "true");
    await expect(page.locator("#routePopAddr")).toContainText(shown.info.split("주소 : ")[1]);
    await expect(page.locator("#routePopKakao")).toHaveAttribute("href", /^https:\/\/map\.kakao\.com\/link\/to\//);
    await expect(page.locator("#routePopNaver")).toHaveAttribute("href", /^https:\/\/map\.naver\.com\//);
    await expect(page.locator("#routePopCopy")).toBeVisible(); // 주소가 있으면 [주소 복사]

    // 창은 단추 근처 화면 안에
    const box = await pop.boundingBox();
    expect(box.x).toBeGreaterThanOrEqual(0);
    expect(box.y).toBeGreaterThanOrEqual(0);
    expect(box.x + box.width).toBeLessThanOrEqual(PC.width);
    expect(box.y + box.height).toBeLessThanOrEqual(PC.height);

    await page.locator("#routePopClose").click();
    await expect(pop).toBeHidden();
    await expect(route).not.toHaveAttribute("aria-expanded", /.*/);
    await expect(route).toBeFocused();

    // Esc 로도 닫힘
    await route.click();
    await expect(pop).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(pop).toBeHidden();
    await expect(route).toBeFocused();
  });
});

test.describe("모바일", () => {
  test.use({ viewport: MOBILE, hasTouch: true });

  test("행사 상세: 지도 탭 카드를 누르면 상세 창이 열리고 뒤로 단추로 닫힘", async ({ page }) => {
    await openMobileMap(page);
    const cards = page.locator("#previewStrip [data-event-id]");

    const card = cards.first();
    const cardTitle = await card.getAttribute("aria-label");
    expect(TITLES).toContain(cardTitle);

    const modal = page.locator("#detailModal");
    await expect(modal).toBeHidden();
    await card.click();
    await expect(modal).toBeVisible();
    await expect(page.locator("#modalTitle")).toHaveText(cardTitle);

    await page.locator("#detailBackBtn").click();
    await expect(modal).toBeHidden();
  });

  test("카드 키보드: Tab 으로 카드에 들어가 Enter 로 상세", async ({ page }) => {
    await openMobileMap(page);

    // 지도 탭 단추에서부터 Tab 만 눌러 카드 줄까지 (카드가 Tab 순서에 들어 있는지)
    await page.locator('#tabBar [data-tab="map"]').focus();
    let focused = null;
    for (let i = 0; i < 60 && !focused; i++) {
      await page.keyboard.press("Tab");
      focused = await page.evaluate(() => {
        const el = document.activeElement;
        return el && el.closest("#previewStrip") && el.dataset.eventId ? el.getAttribute("aria-label") : null;
      });
    }
    expect(focused, "Tab 60번 안에 카드 줄에 닿아야 함").not.toBeNull();

    // 다음 카드도 Tab 한 번 (카드마다 tabIndex 0)
    await page.keyboard.press("Tab");
    const next = await page.evaluate(() => document.activeElement.getAttribute("aria-label"));
    expect(TITLES).toContain(next);
    expect(next).not.toBe(focused);

    await page.keyboard.press("Enter");
    await expect(page.locator("#detailModal")).toBeVisible();
    await expect(page.locator("#modalTitle")).toHaveText(next);
  });
});
