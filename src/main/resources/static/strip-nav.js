// 지도 하단 카드 줄 넘기기 (11/11) — 좌우 화살표 버튼 · 휠 · 키보드 ← → 로 한 장씩 + 위치 표시 글
//  - 터치 · 트랙패드 좌우는 브라우저 기본 가로 스크롤 그대로
//  - 화살표는 처음 카드에서 이전, 끝 카드에서 다음을 숨김
//  - 휠은 세로가 더 클 때만 한 장 (가로가 크면 트랙패드 좌우라 기본 스크롤), 한 번 굴림에 여러 장 넘어가지 않게 잠깐 쉼
// 시험: node --test src/test/js/ (CI 에서 실행)

var WHEEL_GAP_MS = 350;

// 지금 카드(current)에서 delta 만큼 옮긴 카드 번호 (처음 · 끝을 넘지 않음, 카드가 없으면 -1)
function stripStepTarget(current, delta, count) {
  if (count <= 0) return -1;
  var from = current < 0 ? 0 : current;
  return Math.max(0, Math.min(count - 1, from + delta));
}

// 화살표 보이기: 이전 · 다음 카드가 있을 때만
function stripArrowState(index, count) {
  return { prev: count > 1 && index > 0, next: count > 1 && index < count - 1 };
}

// 휠 한 번 → 옮길 장 수 (1 · -1 · 0). lastStepAt = 직전에 휠로 옮긴 시각(ms)
function wheelStep(deltaX, deltaY, lastStepAt, now) {
  if (Math.abs(deltaY) <= Math.abs(deltaX)) return 0;
  if (now - lastStepAt < WHEEL_GAP_MS) return 0;
  return deltaY > 0 ? 1 : -1;
}

// 스크롤이 멈췄을 때 고를 카드 번호. cards = [{ left, width }] (줄 안 위치), viewLeft = scrollLeft, viewWidth = 줄 폭
//  - 지금 고른 카드(current)가 줄 안에 다 보이면 그대로: 넓은 PC 줄에서 첫 · 끝 카드는 가운데로 못 오므로
//    "가운데에 가장 가까운 카드"로만 고르면 옆 카드로 바뀌고 지도까지 옮겨감 (#206 검증 지적)
//  - 아니면(손으로 밀어 화면 밖으로 나감) 가운데에 가장 가까운 카드 — 휴대폰은 한 장만 다 보여서 예전과 같음
function stripSettleIndex(current, cards, viewLeft, viewWidth) {
  if (cards.length === 0) return -1;
  var viewRight = viewLeft + viewWidth;
  var now = cards[current];
  if (now && now.left >= viewLeft - 1 && now.left + now.width <= viewRight + 1) return current;
  var center = viewLeft + viewWidth / 2;
  var best = 0;
  var bestDist = Infinity;
  cards.forEach(function (card, i) {
    var dist = Math.abs(card.left + card.width / 2 - center);
    if (dist < bestDist) {
      bestDist = dist;
      best = i;
    }
  });
  return best;
}

// 카드 줄 위 위치 표시. nearOrder = 마커 · 목록을 눌러 그 행사에서 가까운 순으로 다시 놓았나
//  - PC 는 지도를 열면 카드 줄을 기본으로 띄움(11/11) → 그때는 오른쪽 목록 순서 그대로라 "목록 순"
//  - 고른 카드가 없으면(index < 0) 0 이 아니라 1 부터 셈 → "· 0 / N" 이 보이지 않게
function stripLabelText(nearOrder, index, count) {
  if (count <= 0) return "";
  return (nearOrder ? "가까운 순" : "목록 순") + " · " + (Math.max(index, 0) + 1) + " / " + count;
}

// 키보드 ← → → 옮길 장 수. k = { key, tag(포커스 요소 태그), editable, role, alt(조합키), handled(이미 처리됨) }
// 입력칸에서 치는 중 · 탭 줄처럼 자기 화살표 키를 쓰는 곳 · 조합키(Alt+← 뒤로 가기 등)는 그대로 둠
function stripKeyStep(k) {
  if (k.handled || k.alt || k.editable || k.role === "tab") return 0;
  if (k.tag === "INPUT" || k.tag === "TEXTAREA" || k.tag === "SELECT") return 0;
  if (k.key === "ArrowRight") return 1;
  if (k.key === "ArrowLeft") return -1;
  return 0;
}

if (typeof module !== "undefined") {
  module.exports = {
    WHEEL_GAP_MS: WHEEL_GAP_MS,
    stripStepTarget: stripStepTarget,
    stripArrowState: stripArrowState,
    wheelStep: wheelStep,
    stripSettleIndex: stripSettleIndex,
    stripKeyStep: stripKeyStep,
    stripLabelText: stripLabelText,
  };
}
