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
const SCREENSHOT_DIR = path.join(OUT_DIR, 'ui-i18n-switch-screenshots');
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

async function health() {
  const response = await fetch(`${HUBBLE}/actuator/health`);
  if (!response.ok) {
    throw new Error(`Hubble health returned HTTP ${response.status}`);
  }
  return response.json();
}

async function isHubbleUp() {
  try {
    await health();
    return true;
  } catch (_) {
    return false;
  }
}

async function waitHealth(timeoutMs = 90000) {
  const deadline = Date.now() + timeoutMs;
  let lastError = '';
  while (Date.now() < deadline) {
    try {
      return await health();
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

function trimText(text, length = 500) {
  return text.replace(/\s+/g, ' ').trim().slice(0, length);
}

async function screenshot(page, name) {
  const file = path.join(SCREENSHOT_DIR, `${name}.png`);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

async function switchLanguage(page, labelPattern) {
  await page.locator('.i18n-box .ant-select').click({ timeout: 10000 });
  const option = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
                     .filter({ hasText: labelPattern });
  await option.first().click({ timeout: 10000 });
  await page.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
  await waitRoot(page);
  await page.waitForTimeout(1500);
}

async function expectLanguage(page, storageValue, textPattern, message) {
  const actualStorage = await page.evaluate(() => localStorage.getItem('languageType'));
  if (actualStorage !== storageValue) {
    throw new Error(`${message}: expected localStorage ${storageValue}, got ${actualStorage}`);
  }
  const text = await visibleText(page);
  if (!textPattern.test(text)) {
    throw new Error(`${message}: page text did not match ${textPattern}. Text sample: ${trimText(text)}`);
  }
  return text;
}

async function main() {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
  const report = {
    started_at: new Date().toISOString(),
    status: 'FAILED',
    browser: 'chromium',
    hubble_url: HUBBLE,
    checks: {}
  };
  let started = false;
  let browser = null;

  try {
    const alreadyRunning = await isHubbleUp();
    report.checks.hubble_preexisting = alreadyRunning;
    if (!alreadyRunning) {
      const start = runCommand('./start-hubble.sh', [], path.join(DIST, 'bin'));
      report.checks.hubble_start = start.output;
      if (start.code !== 0) {
        throw new Error(`start-hubble.sh failed: ${start.output}`);
      }
      started = true;
    }
    report.checks.hubble_health = await waitHealth();

    browser = await chromium.launch({ headless: true });
    const context = await browser.newContext({
      viewport: { width: 1440, height: 960 },
      deviceScaleFactor: 1,
      locale: 'zh-CN'
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
        method: request.method(),
        failure: request.failure() && request.failure().errorText
      });
    });

    await page.goto(HUBBLE, { waitUntil: 'domcontentloaded' });
    await waitRoot(page);
    await page.evaluate(() => localStorage.setItem('languageType', 'zh-CN'));
    await page.reload({ waitUntil: 'domcontentloaded' });
    await waitRoot(page);
    await page.waitForTimeout(1200);

    const zhBefore = await expectLanguage(
      page,
      'zh-CN',
      /图管理|中文/,
      'initial Chinese render failed'
    );
    const zhBeforeShot = await screenshot(page, 'zh-before-switch');

    await switchLanguage(page, /English/);
    const enText = await expectLanguage(
      page,
      'en-US',
      /Graph Manager|Create Graph|English/i,
      'runtime switch to English failed'
    );
    const enShot = await screenshot(page, 'en-after-switch');

    await switchLanguage(page, /中文/);
    const zhAfter = await expectLanguage(
      page,
      'zh-CN',
      /图管理|创建图|中文/,
      'runtime switch back to Chinese failed'
    );
    const zhAfterShot = await screenshot(page, 'zh-after-switch-back');

    const severeConsoleErrors = consoleErrors.filter((message) => {
      return !/favicon|ResizeObserver loop limit exceeded|Warning: componentWillReceiveProps/i.test(message);
    });
    const severeFailedRequests = failedRequests.filter((request) => {
      return !/favicon/i.test(request.url);
    });

    report.checks.language_switch = {
      zh_before_storage: 'zh-CN',
      en_after_storage: 'en-US',
      zh_after_storage: 'zh-CN',
      zh_before_text_sample: trimText(zhBefore),
      en_text_sample: trimText(enText),
      zh_after_text_sample: trimText(zhAfter),
      screenshots: [zhBeforeShot, enShot, zhAfterShot]
    };
    report.checks.console_errors = consoleErrors;
    report.checks.page_errors = pageErrors;
    report.checks.failed_requests = failedRequests;
    report.checks.severe_console_errors = severeConsoleErrors;
    report.checks.severe_failed_requests = severeFailedRequests;

    if (severeConsoleErrors.length > 0 || pageErrors.length > 0 || severeFailedRequests.length > 0) {
      throw new Error(
        `i18n switch saw browser errors: console=${severeConsoleErrors.length}, ` +
        `page=${pageErrors.length}, requests=${severeFailedRequests.length}`
      );
    }

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
    } else {
      report.checks.hubble_stop = ['skipped; Hubble was already running or start failed before ownership', 0];
    }
    report.finished_at = new Date().toISOString();

    const jsonPath = path.join(OUT_DIR, 'hubble_ui_i18n_switch_result.json');
    const mdPath = path.join(OUT_DIR, 'hubble_ui_i18n_switch_result.md');
    fs.writeFileSync(jsonPath, `${JSON.stringify(report, null, 2)}\n`);

    const lines = [
      '### UI Runtime i18n Switch Smoke - 2026-06-09',
      '',
      `- Status: \`${report.status}\``,
      `- Browser: \`${report.browser}\``,
      `- Hubble URL: \`${report.hubble_url}\``,
      '- Flow: `zh-CN -> en-US -> zh-CN` through the visible AppBar language selector',
      `- Console errors: \`${(report.checks.console_errors || []).length}\``,
      `- Page errors: \`${(report.checks.page_errors || []).length}\``,
      `- Failed requests: \`${(report.checks.failed_requests || []).length}\``,
      `- Severe console errors: \`${(report.checks.severe_console_errors || []).length}\``,
      `- Severe failed requests: \`${(report.checks.severe_failed_requests || []).length}\``,
      report.error ? `- Error: \`${report.error.split('\n')[0]}\`` : '',
      `- Screenshots: \`${SCREENSHOT_DIR}\``,
      report.checks.hubble_stop ? `- Hubble stop: \`${report.checks.hubble_stop[0]}\`` : ''
    ].filter(Boolean);
    fs.writeFileSync(mdPath, `${lines.join('\n')}\n`);
    console.log(`wrote ${jsonPath}`);
    console.log(`wrote ${mdPath}`);
    console.log(JSON.stringify(report, null, 2));
  }
}

main();
