// L 등급 경로 판정 · gh pr merge 명령 해석 시험 (.claude/hooks/grade.js)
const test = require("node:test");
const assert = require("node:assert");
const { isLPath, lPaths, parseMergeCommand } = require("../../../.claude/hooks/grade.js");

test("L 경로: 등급표(CLAUDE.md 4번)의 경로는 L", () => {
  for (const p of [
    "CLAUDE.md",
    ".claude/settings.json",
    ".claude/hooks/guard-l-merge.js",
    ".github/workflows/ci.yml",
    "src/main/java/com/suwon/festival/security/OAuthState.java",
    "src/main/java/com/suwon/festival/config/WebConfig.java",
    "src/main/java/com/suwon/festival/entity/Event.java",
    "src/main/resources/db/migration/V9__x.sql",
    "src/main/resources/application.properties",
    "src/main/resources/application-prod.properties",
    "Dockerfile",
    "docker-compose.yml",
    "docker-compose.prod.yml",
    "deploy/deploy.sh",
    "Jenkinsfile",
    ".\\deploy\\Caddyfile",
  ]) {
    assert.strictEqual(isLPath(p), true, p);
  }
});

test("L 아님: 시험용 설정 · 그 밖 자바 · 화면 · 문서", () => {
  for (const p of [
    "src/test/resources/application-test.properties",
    "src/main/java/com/suwon/festival/service/SyncService.java",
    "src/main/java/com/suwon/festival/controller/EventController.java",
    "src/main/resources/static/index.html",
    "PROGRESS.md",
    "docs/CLAUDE.md",
    "docs/deploy-ec2.md",
    "scripts/setup-dev.cmd",
  ]) {
    assert.strictEqual(isLPath(p), false, p);
  }
});

test("lPaths: L 경로만 골라냄", () => {
  assert.deepStrictEqual(lPaths(["PROGRESS.md", ".github/x.yml", "README.md"]), [".github/x.yml"]);
});

test("parseMergeCommand: 번호 · URL · 현재 브랜치 · --auto", () => {
  assert.deepStrictEqual(parseMergeCommand("gh pr merge 229 --merge"), { merge: true, pr: "229", repo: null, auto: false });
  assert.deepStrictEqual(parseMergeCommand("gh pr merge --merge"), { merge: true, pr: null, repo: null, auto: false });
  assert.deepStrictEqual(
    parseMergeCommand("gh pr merge https://github.com/o/r/pull/12 --merge"),
    { merge: true, pr: "12", repo: null, auto: false }
  );
  assert.strictEqual(parseMergeCommand("gh pr merge 5 --auto --merge").auto, true);
  assert.strictEqual(parseMergeCommand("cd x && gh pr merge 7 --merge; echo ok").pr, "7");
});

test("parseMergeCommand: 따옴표 전체 경로 · -R · gh api · 옵션 값 안 숫자", () => {
  assert.strictEqual(parseMergeCommand("& 'C:\\Program Files\\GitHub CLI\\gh.exe' pr merge 229 --merge").pr, "229");
  assert.strictEqual(parseMergeCommand('"/c/Program Files/GitHub CLI/gh.exe" pr merge 229').pr, "229");
  assert.deepStrictEqual(parseMergeCommand("gh pr -R o/r merge 31 --merge"), { merge: true, pr: "31", repo: "o/r", auto: false });
  assert.strictEqual(parseMergeCommand("gh pr merge --repo o/r 31").repo, "o/r");
  assert.deepStrictEqual(
    parseMergeCommand("gh api -X PUT repos/o/r/pulls/44/merge -f merge_method=merge"),
    { merge: true, pr: "44", repo: "o/r", auto: false }
  );
  assert.strictEqual(parseMergeCommand('gh pr merge --subject "Merge 12 fixes" 229').pr, "229");
  assert.strictEqual(parseMergeCommand("gh pr merge -t x 229 --merge").pr, "229");
});

test("parseMergeCommand: 머지가 아닌 명령은 무시", () => {
  assert.strictEqual(parseMergeCommand("gh pr view 229").merge, false);
  assert.strictEqual(parseMergeCommand("git merge origin/main").merge, false);
  assert.strictEqual(parseMergeCommand("").merge, false);
});
