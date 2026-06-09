const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');
const ROOT = path.resolve(__dirname, '../..');
const DIST = path.join(
  ROOT,
  'hugegraph-hubble',
  'hubble-dist',
  'apache-hugegraph-hubble-1.7.0'
);
const OUT_DIR = path.join(ROOT, '.workflow', 'hubble-v2-release');
const SCREENSHOT_DIR = path.join(OUT_DIR, 'ui-browser-smoke-screenshots');
const HUBBLE = 'http://127.0.0.1:8088';
function runCommand(command, args, cwd) {
  const result = spawnSync(command, args, {
    cwd,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe']
  });
  return {
    code: result.status,
    output: `${result.stdout || ''}${result.stderr || ''}`.trim()
  };
}
async function waitHealth(timeoutMs = 90000) {
  const deadline = Date.now() + timeoutMs;
  let lastError = '';
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`${HUBBLE}/actuator/health`);
      if (response.ok) {
        return await response.json();
      }
      lastError = `HTTP ${response.status}`;
    } catch (error) {
      lastError = String(error);
    }
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  throw new Error(`Timed out waiting for Hubble health: ${lastError}`);
}
async function waitRoot(page) {
  await page.waitForFunction(() => {
    const root = document.querySelector('#root');
    return root && root.textContent && root.textContent.trim().length > 0;
  }, { timeout: 30000 });
}
async function visibleText(page) {
  return page.evaluate(() => document.body.innerText.replace(/\s+/g, ' ').trim());
}
async function routeSmoke(page, route, name) {
  await page.goto(`${HUBBLE}${route}`, { waitUntil: 'domcontentloaded' });
  await waitRoot(page);
  await page.waitForTimeout(1500);
  const text = await visibleText(page);
  if (text.length < 20) {
    throw new Error(`${name} rendered too little visible text: ${text}`);
  }
  const file = path.join(SCREENSHOT_DIR, `${name}.png`);
  await page.screenshot({ path: file, fullPage: true });
  return {
    route,
    title: await page.title(),
    text_sample: text.slice(0, 300),
    screenshot: file
  };
}
async function main() {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
  const report = {
    started_at: new Date().toISOString(),
    status: 'FAILED',
    checks: {},
    browser: 'chromium'
  };
  let started = false;
  let browser = null;
  try {
    const start = runCommand('./start-hubble.sh', [], path.join(DIST, 'bin'));
    report.checks.hubble_start = start.output;
    if (start.code !== 0) {
      throw new Error(`start-hubble.sh failed: ${start.output}`);
    }
    started = true;
    await waitHealth();
    browser = await chromium.launch({ headless: true });
    const context = await browser.newContext({
      viewport: { width: 1440, height: 960 },
      deviceScaleFactor: 1
    });
    const page = await context.newPage();
    const consoleErrors = [];
    const pageErrors = [];
    const failedRequests = [];
    page.on('console', (message) => {
      if (message.type() === 'error') {
        consoleErrors.push(message.text());
      }
    });
    page.on('pageerror', (error) => pageErrors.push(String(error)));
    page.on('requestfailed', (request) => {
      failedRequests.push({
        url: request.url(),
        failure: request.failure() && request.failure().errorText
      });
    });

    const home = await routeSmoke(page, '/', 'home');
    const homeText = home.text_sample;
    if (!/Graph Manager|图管理|图列表|Create Graph|新建图|创建图/.test(homeText)) {
      throw new Error(`Home page missing expected graph management text: ${homeText}`);
    }
    report.checks.home = home;

    const configResponse = await page.request.get(`${HUBBLE}/api/v1.2/graph-connections?page_no=1&page_size=20`);
    if (!configResponse.ok()) {
      throw new Error(`graph-connections API failed: ${configResponse.status()}`);
    }
    const connectionsPayload = await configResponse.json();
    const records = (connectionsPayload.data && connectionsPayload.data.records) || [];
    report.checks.connection_count = records.length;
    const connId = records.length > 0 ? records[0].id : 1;
    report.checks.routes = [];
    for (const [route, name] of [
      [`/graph-management/${connId}/data-analyze`, 'data-analyze'],
      [`/graph-management/${connId}/metadata-configs`, 'metadata-configs'],
      [`/graph-management/${connId}/data-import/import-manager`, 'data-import'],
      [`/graph-management/${connId}/async-tasks`, 'async-tasks']
    ]) {
      report.checks.routes.push(await routeSmoke(page, route, name));
    }

    const severeConsoleErrors = consoleErrors.filter((message) => {
      return !/favicon|ResizeObserver loop limit exceeded/i.test(message);
    });
    if (pageErrors.length > 0 || failedRequests.length > 0 || severeConsoleErrors.length > 0) {
      report.checks.console_errors = consoleErrors;
      report.checks.page_errors = pageErrors;
      report.checks.failed_requests = failedRequests;
      throw new Error('Browser smoke saw console/page/request errors');
    }
    report.checks.console_errors = consoleErrors;
    report.checks.page_errors = pageErrors;
    report.checks.failed_requests = failedRequests;
    report.status = 'SUCCESS';
  } catch (error) {
    report.error = String(error && error.stack ? error.stack : error);
    process.exitCode = 1;
  } finally {
    if (browser) {
      await browser.close();
    }
    if (started) {
      const stop = runCommand('./stop-hubble.sh', [], path.join(DIST, 'bin'));
      report.checks.hubble_stop = [stop.output, stop.code];
    }
    report.finished_at = new Date().toISOString();
    const jsonPath = path.join(OUT_DIR, 'hubble_ui_browser_smoke_result.json');
    const mdPath = path.join(OUT_DIR, 'hubble_ui_browser_smoke_result.md');
    fs.writeFileSync(jsonPath, `${JSON.stringify(report, null, 2)}\n`);
    const routeCount = report.checks.routes ? report.checks.routes.length : 0;
    fs.writeFileSync(
      mdPath,
      [
        '### UI Browser Smoke - 2026-06-09',
        '',
        `- Status: \`${report.status}\``,
        `- Browser: \`${report.browser}\``,
        `- Home screenshot: \`${report.checks.home ? report.checks.home.screenshot : ''}\``,
        `- Routed pages checked: \`${routeCount}\``,
        `- Connection count from UI API: \`${report.checks.connection_count}\``,
        `- Console errors: \`${(report.checks.console_errors || []).length}\``,
        `- Page errors: \`${(report.checks.page_errors || []).length}\``,
        `- Failed requests: \`${(report.checks.failed_requests || []).length}\``,
        report.error ? `- Error: \`${report.error.split('\n')[0]}\`` : '',
        report.checks.hubble_stop ? `- Hubble stop: \`${report.checks.hubble_stop[0]}\`` : ''
      ].filter(Boolean).join('\n') + '\n'
    );
    console.log(`wrote ${jsonPath}`);
    console.log(`wrote ${mdPath}`);
    console.log(JSON.stringify(report, null, 2));
  }
}

main();
