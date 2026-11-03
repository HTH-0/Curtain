// 공개 사이트를 Chrome(원격 제어, CDP)으로 열어 README 사진 3장을 찍는다(1200x700). hero.png, flow.png, portal-raw.png가 나온다.
// 사용법: node tools/shoot-screens.mjs <출력폴더> (Chrome과 Node 22 이상 필요, portal-raw.png는 portal-demo.png로 복사)
import { spawn } from "node:child_process";
import { mkdirSync, writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const OUT = process.argv[2];
const URL = "https://curtain-h8id.onrender.com/";
const PORT = 9333;
mkdirSync(OUT, { recursive: true });

const chrome = spawn("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe", [
  "--headless=new", `--remote-debugging-port=${PORT}`, `--user-data-dir=${mkdtempSync(join(tmpdir(), "shoot-"))}`,
  "--hide-scrollbars", "--force-device-scale-factor=1", "--no-first-run", "--disable-gpu", "about:blank",
], { stdio: "ignore" });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let ws, id = 0; const pending = new Map();
async function connect() {
  for (let i = 0; i < 50; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json`)).json();
      const page = list.find((t) => t.type === "page");
      if (page) { ws = new WebSocket(page.webSocketDebuggerUrl); break; }
    } catch {}
    await sleep(200);
  }
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });
  ws.onmessage = (e) => { const m = JSON.parse(e.data); if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); } };
}
const send = (method, params = {}) => new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })); });
const ev = async (expr) => (await send("Runtime.evaluate", { expression: expr, returnByValue: true, awaitPromise: true })).result?.result?.value;
const shot = async (name) => {
  const r = await send("Page.captureScreenshot", { format: "png" });
  writeFileSync(join(OUT, name), Buffer.from(r.result.data, "base64")); console.log("저장", name);
};

try {
  await connect();
  await send("Page.enable");
  await send("Emulation.setDeviceMetricsOverride", { width: 1200, height: 700, deviceScaleFactor: 1, mobile: false });
  await send("Page.navigate", { url: URL });
  for (let i = 0; i < 60 && (await ev("document.readyState")) !== "complete"; i++) await sleep(500);
  await sleep(2500); // 커튼이 열리는 인트로(약 1.4초)가 끝나길 기다린다

  await shot("hero.png");

  // 흐름도: 5단계가 모두 켜지는 위치까지 스크롤한다(닫힌 뒤 화면 높이의 0.63배마다 한 칸)
  const y5 = await ev(`(() => { const s = document.querySelector(".stage"); return s.offsetTop + innerHeight + innerHeight * 0.63 * 4 + 20; })()`);
  await ev(`window.scrollTo(0, ${y5})`);
  await sleep(1500);
  console.log("흐름도 단계:", await ev(`document.querySelector(".hero").dataset.step`));
  await shot("flow.png");

  // 포털: 가림 예시 버튼을 눌러 커튼 기록이 붙은 답을 만든다
  await ev(`document.querySelector("#portal").scrollIntoView()`);
  await sleep(800);
  const clicked = await ev(`(() => { const b = [...document.querySelectorAll("#examples button")].find((x) => x.textContent.includes("고객 연락처")); if (!b) return false; b.click(); return true; })()`);
  console.log("예시 버튼 클릭:", clicked);
  await sleep(500);
  await ev(`document.querySelector("#send").click()`);
  await sleep(6000);
  console.log("커튼 기록 위치 맞춤:", await ev(`(() => {
    const log = document.querySelector("#log");
    const s = [...log.querySelectorAll("summary")].pop();
    if (!s) return "summary 없음";
    const d = s.closest("details"); if (d) d.open = true;
    log.scrollTop += s.getBoundingClientRect().top - log.getBoundingClientRect().top - 10;
    return "ok " + log.scrollTop;
  })()`));
  await sleep(500);
  const card = `document.querySelector("#log").parentElement`;
  await ev(`(() => { const r = ${card}.getBoundingClientRect(); window.scrollTo(0, r.top + scrollY - 50); })()`);
  await sleep(800);
  console.log("카드:", JSON.stringify(await ev(`(() => { const r = ${card}.getBoundingClientRect(); return { top: r.top, h: r.height }; })()`)));
  await shot("portal-raw.png");
  console.log("로그 스크롤:", JSON.stringify(await ev(`(() => { const l = document.querySelector("#log"); return { top: l.scrollTop, h: l.scrollHeight, c: l.clientHeight, y: scrollY }; })()`)));
} finally {
  try { ws?.close(); } catch {}
  chrome.kill();
}
