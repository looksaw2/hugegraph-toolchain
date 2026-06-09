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
const SCREENSHOT_DIR = path.join(OUT_DIR, 'ui-full-acceptance-screenshots');
const HUBBLE = 'http://127.0.0.1:8088';
const API = `${HUBBLE}/api/v1.2/graph-connections`;

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

async function gotoPage(page, route) {
  await page.goto(`${HUBBLE}${route}`, { waitUntil: 'domcontentloaded' });
  await waitRoot(page);
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(800);
}

async function screenshot(page, name) {
  const file = path.join(SCREENSHOT_DIR, `${name}.png`);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

function trimText(text, length = 500) {
  return text.replace(/\s+/g, ' ').trim().slice(0, length);
}

function assertMatch(text, pattern, message) {
  if (!pattern.test(text)) {
    throw new Error(`${message}. Text sample: ${trimText(text)}`);
  }
}

async function expectText(page, pattern, message) {
  const text = await visibleText(page);
  assertMatch(text, pattern, message);
  return text;
}

async function clickByText(page, pattern, options = {}) {
  const locator = page.getByText(pattern, { exact: options.exact || false });
  await locator.first().click({ timeout: options.timeout || 10000 });
}

async function clickVisibleByText(page, pattern, options = {}) {
  const locator = page.getByText(pattern, { exact: options.exact || false });
  const count = await locator.count();
  for (let i = 0; i < count; i += 1) {
    const candidate = locator.nth(i);
    if (await candidate.isVisible().catch(() => false)) {
      await candidate.click({ timeout: options.timeout || 10000 });
      return true;
    }
  }
  throw new Error(`No visible element found for text ${pattern}`);
}

async function safeClickByText(page, pattern, options = {}) {
  const locator = page.getByText(pattern, { exact: options.exact || false });
  const count = await locator.count();
  for (let i = 0; i < count; i += 1) {
    const candidate = locator.nth(i);
    if (await candidate.isVisible().catch(() => false)) {
      await candidate.click({ timeout: options.timeout || 10000 });
      return true;
    }
  }
  return false;
}

async function clickVisible(page, selector, options = {}) {
  const locator = page.locator(selector);
  const count = await locator.count();
  for (let i = 0; i < count; i += 1) {
    const candidate = locator.nth(i);
    if (await candidate.isVisible().catch(() => false)) {
      await candidate.click({ timeout: options.timeout || 10000 });
      return true;
    }
  }
  throw new Error(`No visible element found for selector ${selector}`);
}

async function clickVisibleWithin(page, selector, pattern, options = {}) {
  const locator = page.locator(selector).filter({ hasText: pattern });
  const count = await locator.count();
  for (let i = 0; i < count; i += 1) {
    const candidate = locator.nth(i);
    if (await candidate.isVisible().catch(() => false)) {
      await candidate.click({ timeout: options.timeout || 10000 });
      return true;
    }
  }
  throw new Error(`No visible element found for selector ${selector} with text ${pattern}`);
}

async function clickVisibleButtonByText(page, pattern, options = {}) {
  return clickVisibleWithin(page, 'button, [role="button"]', pattern, options);
}

async function clickMetadataMenuItem(page, pattern) {
  return clickVisibleWithin(
    page,
    '.metadata-configs-content [class*="menu"] li, .metadata-configs-content [class*="menu"] div',
    pattern
  );
}

async function countVisible(page, selector) {
  const locator = page.locator(selector);
  const count = await locator.count();
  let visible = 0;
  for (let i = 0; i < count; i += 1) {
    if (await locator.nth(i).isVisible().catch(() => false)) {
      visible += 1;
    }
  }
  return visible;
}

async function cancelGraphManagementEmbeddedForm(page) {
  await clickVisibleButtonByText(page, /取\s*消|Cancel/i);
  await page
    .locator('.graph-management-list-manipulation button')
    .filter({ hasText: /更\s*多|More/i })
    .first()
    .waitFor({ state: 'visible', timeout: 10000 });
  await page.waitForFunction(() => {
    const buttons = Array.from(
      document.querySelectorAll('.graph-management-list-manipulation button')
    );
    const more = buttons.find((button) => /更\s*多|More/i.test(button.textContent || ''));
    return more && !more.disabled;
  }, { timeout: 10000 });
}

async function setCodeMirror(page, value) {
  await page.locator('.CodeMirror').first().waitFor({ state: 'visible', timeout: 15000 });
  await page.evaluate((query) => {
    const element = document.querySelector('.CodeMirror');
    if (!element || !element.CodeMirror) {
      throw new Error('CodeMirror instance not found');
    }
    element.CodeMirror.setValue(query);
    element.CodeMirror.focus();
  }, value);
}

async function getApiJson(request, url) {
  const response = await request.get(url);
  if (!response.ok()) {
    throw new Error(`${url} failed with HTTP ${response.status()}`);
  }
  const json = await response.json();
  if (json.status && json.status !== 200) {
    throw new Error(`${url} returned business status ${json.status}: ${json.message}`);
  }
  return json;
}

async function checkPage(report, page, name, route, expectedPattern) {
  await gotoPage(page, route);
  const text = await expectText(page, expectedPattern, `${name} missing expected text`);
  const shot = await screenshot(page, name);
  report.checks[name] = {
    route,
    text_sample: trimText(text),
    screenshot: shot
  };
}

async function sidebarAcceptance(report, page, connId) {
  await gotoPage(page, `/graph-management/${connId}/data-analyze`);
  const shots = [];
  await clickVisible(page, '.data-analyze-sidebar-expand-control');
  await page.waitForTimeout(700);
  let text = await expectText(page, /数据分析|Data analysis|元数据配置|Metadata|数据导入|Data import|任务管理|Task management/, 'expanded sidebar did not show main entries');
  shots.push(await screenshot(page, 'sidebar-expanded'));

  for (const [label, urlPattern] of [
    [/元数据配置|Metadata/i, /\/metadata-configs/],
    [/数据导入|Data import/i, /\/data-import\/import-manager/],
    [/任务管理|Task management/i, /\/async-tasks/],
    [/数据分析|Data analysis/i, /\/data-analyze/]
  ]) {
    await clickVisibleByText(page, label);
    await page.waitForURL(urlPattern, { timeout: 15000 });
    await waitRoot(page);
    await page.waitForTimeout(700);
  }

  await clickVisible(page, '.data-analyze-sidebar-expand-control');
  await page.waitForTimeout(700);
  text = await visibleText(page);
  shots.push(await screenshot(page, 'sidebar-collapsed'));
  report.checks.sidebar_navigation = {
    covered: ['expand', 'collapse', 'data analyze', 'metadata', 'data import', 'async tasks'],
    text_sample: trimText(text),
    screenshots: shots
  };
}

async function graphManagementAcceptance(report, page, conn) {
  await gotoPage(page, '/');
  let text = await expectText(
    page,
    /图管理|Graph Manager|Graph Management/,
    'graph management home did not render'
  );
  assertMatch(text, new RegExp(conn.name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')), 'home missing current graph connection');

  const searchInput = page.locator('.graph-management-header input').first();
  await searchInput.fill(conn.name);
  await page.keyboard.press('Enter');
  await page.waitForTimeout(1000);
  text = await visibleText(page);
  assertMatch(text, new RegExp(conn.name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')), 'graph search did not keep expected connection visible');
  await screenshot(page, 'graph-management-search');

  await searchInput.fill('');
  await page.keyboard.press('Enter');
  await page.waitForTimeout(800);

  await clickVisibleWithin(page, '.graph-management-header button', /创建图|Create Graph|Create/i);
  await page.waitForTimeout(500);
  text = await expectText(page, /图ID|Graph ID|主机名|Host|端口号|Port/, 'create graph form did not open');
  await screenshot(page, 'graph-management-create-form');
  await cancelGraphManagementEmbeddedForm(page);
  await page.waitForTimeout(500);

  await page
    .locator('.graph-management-list-manipulation')
    .first()
    .locator('button')
    .last()
    .click({ timeout: 10000 });
  await page.waitForTimeout(500);
  await clickVisibleByText(page, /编\s*辑|Edit/i);
  await page.waitForTimeout(500);
  text = await expectText(page, /编辑图|Graph Edit|保存|Save|密码|Password/, 'edit graph form did not open');
  const disabledCount = await page.locator('.graph-management-list-data-config input[disabled]').count();
  if (disabledCount < 4) {
    throw new Error(`edit graph form expected at least 4 immutable inputs, got ${disabledCount}`);
  }
  await screenshot(page, 'graph-management-edit-form');
  await cancelGraphManagementEmbeddedForm(page);
  await page.waitForTimeout(500);

  await clickVisibleWithin(page, '.graph-management-list-manipulation button', /访\s*问|Visit/i);
  await page.waitForURL(/\/graph-management\/\d+\/data-analyze/, { timeout: 15000 });
  text = await expectText(page, /Gremlin|数据分析|Data analysis|算法分析|Algorithm/, 'visit did not navigate to data analyze');
  report.checks.graph_management = {
    connection: conn,
    disabled_edit_inputs: disabledCount,
    final_route: page.url().replace(HUBBLE, ''),
    text_sample: trimText(text),
    screenshot: await screenshot(page, 'graph-management-visit-data-analyze')
  };
}

async function dataAnalyzeAcceptance(report, page, connId) {
  await gotoPage(page, `/graph-management/${connId}/data-analyze`);
  let text = await expectText(page, /Gremlin 分析|Gremlin analysis|算法分析|Algorithm analysis/, 'data analyze did not render tabs');

  await setCodeMirror(page, 'g.V().limit(1)');
  await clickByText(page, /执行查询|Execute query/i);
  await page.waitForResponse(
    (response) => response.url().includes(`/graph-connections/${connId}/gremlin-query`) &&
      response.request().method() === 'POST',
    { timeout: 30000 }
  ).catch(() => {});
  await page.waitForTimeout(2500);
  text = await expectText(page, /图|表格|Json|Graph|Table|执行记录|Execution record/, 'gremlin query result area did not render');
  await clickVisibleWithin(page, '.query-result-sidebar-options', /表格|Table/i);
  await page.waitForTimeout(500);
  await expectText(page, /表格|Table|result|暂无数据结果|No data/i, 'table result view did not render');
  await clickVisibleWithin(page, '.query-result-sidebar-options', /Json/i);
  await page.waitForTimeout(500);
  await expectText(page, /Json|暂无数据结果|No data|result/i, 'json result view did not render');
  await clickVisibleWithin(page, '.query-result-sidebar-options', /图|Graph/i);
  await page.waitForTimeout(500);

  await clickVisibleWithin(page, '.query-tab-manipulations button', /收\s*藏|Favorite/i);
  await page.waitForTimeout(500);
  await expectText(page, /收藏语句|Favorite statement|收藏名称|Favorite/i, 'favorite popover did not render');
  await safeClickByText(page, /取\s*消|Cancel/);
  await page.waitForTimeout(400);

  const execLogRows = await countVisible(page, '.new-fc-one-table tbody tr');
  const queryShot = await screenshot(page, 'data-analyze-gremlin-query');

  await clickVisibleWithin(page, '.query-tab-index', /算法分析|Algorithm analysis/i);
  await page.waitForTimeout(800);
  text = await expectText(page, /算法目录|Algorithm directory|最短路径|Minimum path|Ring detection/, 'algorithm list did not render');
  await clickVisibleWithin(page, '.query-tab-content-menu span', /最短路径|Minimum path/i);
  await page.waitForTimeout(800);
  text = await expectText(page, /起点ID|Starting point ID|终点ID|End ID|最大步数|Maximum step/, 'shortest path form did not render');
  const algorithmShot = await screenshot(page, 'data-analyze-algorithm-form');

  report.checks.data_analyze = {
    gremlin_query: 'g.V().limit(1)',
    execution_log_visible_rows_after_query: execLogRows,
    query_text_sample: trimText(text),
    screenshots: [queryShot, algorithmShot]
  };
}

async function metadataAcceptance(report, page, connId) {
  await gotoPage(page, `/graph-management/${connId}/metadata-configs`);
  let text = await expectText(page, /列表模式|List mode|属性|Property|顶点类型|Vertex/, 'metadata list page did not render');
  const shots = [await screenshot(page, 'metadata-property-list')];

  const tabs = [
    [/顶点类型|Vertex type/i, /顶点类型名称|Vertex type name|ID策略|ID strategy/i, 'metadata-vertex-list'],
    [/边类型|Edge type/i, /边类型名称|Edge type name|起点类型|source|终点类型|target/i, 'metadata-edge-list'],
    [/属性索引|Property index/i, /顶点索引|Vertex index|边索引|Edge index|索引类型|Index type/i, 'metadata-property-index-list'],
    [/属性(?!索引)|Property(?! index)/i, /属性名称|Property name|数据类型|Data type|基数|Cardinality/i, 'metadata-property-list-return']
  ];

  for (const [tabPattern, expectedPattern, shotName] of tabs) {
    await clickMetadataMenuItem(page, tabPattern);
    await page.waitForTimeout(1200);
    text = await expectText(page, expectedPattern, `${shotName} did not render expected content`);
    shots.push(await screenshot(page, shotName));
  }

  await clickMetadataMenuItem(page, /属性索引|Property index/i);
  await page.waitForTimeout(900);
  await clickVisibleByText(page, /边索引|Edge index/i);
  await page.waitForTimeout(900);
  text = await expectText(page, /边索引|Edge index|索引类型|Index type|暂无|No index/i, 'edge property index tab did not render');
  shots.push(await screenshot(page, 'metadata-property-index-edge-tab'));
  await clickVisibleByText(page, /顶点索引|Vertex index/i);
  await page.waitForTimeout(700);

  await clickMetadataMenuItem(page, /属性(?!索引)|Property(?! index)/i);
  await page.waitForTimeout(800);
  await clickVisibleWithin(page, '.metadata-configs-content-header button', /创\s*建|Create/i);
  await page.waitForTimeout(700);
  text = await expectText(page, /属性名称|Property name|数据类型|Data type|基数|Cardinality/, 'property create row did not render');
  shots.push(await screenshot(page, 'metadata-property-create-row'));
  await safeClickByText(page, /取\s*消|Cancel/);
  await page.waitForTimeout(700);

  await clickMetadataMenuItem(page, /顶点类型|Vertex type/i);
  await page.waitForTimeout(800);
  await clickVisibleWithin(page, '.metadata-configs-content-header button', /创\s*建|Create/i);
  await page.waitForTimeout(1000);
  text = await expectText(page, /基础信息|Base info|顶点类型名称|Vertex type name|ID策略|ID strategy/, 'vertex create page did not render');
  shots.push(await screenshot(page, 'metadata-vertex-create-page'));
  await safeClickByText(page, /取\s*消|Cancel/);
  await page.waitForTimeout(800);

  await clickMetadataMenuItem(page, /边类型|Edge type/i);
  await page.waitForTimeout(800);
  await clickVisibleWithin(page, '.metadata-configs-content-header button', /创\s*建|Create/i);
  await page.waitForTimeout(1000);
  text = await expectText(page, /基础信息|Base info|边类型名称|Edge type name|起点类型|Source/, 'edge create page did not render');
  shots.push(await screenshot(page, 'metadata-edge-create-page'));
  await safeClickByText(page, /取\s*消|Cancel/);
  await page.waitForTimeout(800);

  await clickVisibleWithin(page, '.metadata-configs-content-mode-button', /图模式|Chart mode|Graph mode/i);
  await page.waitForTimeout(1800);
  text = await expectText(page, /图模式|Chart mode|Graph mode|属性|Property|顶点|Vertex/, 'metadata graph mode did not render');
  shots.push(await screenshot(page, 'metadata-graph-mode'));
  await clickVisibleByText(page, /创建属性|Create property/i);
  await page.waitForTimeout(700);
  text = await expectText(page, /创建属性|Create property|属性名称|Property name|数据类型|Data type/, 'graph mode create property drawer did not render');
  shots.push(await screenshot(page, 'metadata-graph-create-property-drawer'));
  await safeClickByText(page, /取\s*消|Cancel/);
  await page.waitForTimeout(500);

  report.checks.metadata_configs = {
    covered: [
      'property list',
      'vertex type list',
      'edge type list',
      'property index vertex/edge tabs',
      'property create row',
      'vertex create page',
      'edge create page',
      'graph mode',
      'graph mode create property drawer'
    ],
    text_sample: trimText(text),
    screenshots: shots
  };
}

async function importAcceptance(report, page, connId, jobs) {
  await gotoPage(page, `/graph-management/${connId}/data-import/import-manager`);
  let text = await expectText(page, /导入任务|Import task|任务名称|Job name|创建|Create/, 'import manager did not render');
  const shots = [await screenshot(page, 'import-manager-list')];

  await clickVisibleWithin(page, '.import-manager-content-header button', /创\s*建|Create/i);
  await page.waitForTimeout(600);
  text = await expectText(page, /创建任务|Create job|任务名称|Job name|任务描述|description/i, 'import create modal did not render');
  shots.push(await screenshot(page, 'import-manager-create-modal'));
  await safeClickByText(page, /取\s*消|Cancel/);
  await page.waitForTimeout(600);

  const completedJob = jobs.find((job) => job.job_status === 'SUCCESS' || job.job_status === 'FAILED');
  const result = {
    job_count: jobs.length,
    opened_completed_job_details: false,
    skipped_job_details_reason: ''
  };

  if (completedJob) {
    await gotoPage(page, `/graph-management/${connId}/data-import/import-manager/${completedJob.id}/details`);
    await page.waitForTimeout(2500);
    text = await expectText(page, /基本设置|Basic settings|已上传文件|Uploaded files|数据映射|Data maps|导入详情|Import details/, 'import job details did not render');
    shots.push(await screenshot(page, 'import-manager-job-details'));
    for (const [tab, expected, shotName] of [
      [/已上传文件|Uploaded files/i, /文件名|File|上传|Upload|大小|Size/i, 'import-job-details-uploaded-files'],
      [/数据映射|Data maps/i, /数据映射|Data maps|属性映射|Property|文件|File/i, 'import-job-details-data-maps'],
      [/导入详情|Import details/i, /导入详情|Import details|任务|Task|状态|Status/i, 'import-job-details-import-details'],
      [/基本设置|Basic settings/i, /任务名称|Job name|任务描述|description|编辑|Edit/i, 'import-job-details-basic-return']
    ]) {
      const clicked = await safeClickByText(page, tab);
      if (clicked) {
        await page.waitForTimeout(900);
        text = await expectText(page, expected, `${shotName} did not render`);
        shots.push(await screenshot(page, shotName));
      }
    }
    result.opened_completed_job_details = true;
    result.job_detail_id = completedJob.id;
  } else {
    result.skipped_job_details_reason = 'No SUCCESS/FAILED import job exists in current Hubble metadata';
  }

  report.checks.data_import = {
    ...result,
    text_sample: trimText(text),
    screenshots: shots
  };
}

async function asyncTasksAcceptance(report, page, connId, tasks) {
  await gotoPage(page, `/graph-management/${connId}/async-tasks`);
  let text = await expectText(page, /任务管理|Task management|任务ID|Task ID|状态|Status/, 'async task list did not render');
  const shots = [await screenshot(page, 'async-task-list')];

  const searchInput = page.locator('.async-task-list-content-header input').first();
  if (await searchInput.count()) {
    await searchInput.fill('g.');
    await page.keyboard.press('Enter');
    await page.waitForTimeout(1200);
    shots.push(await screenshot(page, 'async-task-search'));
    await searchInput.fill('');
    await page.keyboard.press('Enter');
    await page.waitForTimeout(1000);
  }

  const filterShots = [];
  const filterIcons = page.locator('.new-fc-one-table-filter-trigger, .new-fc-one-table-filter-icon, th svg, th img');
  const filterCount = await filterIcons.count();
  if (filterCount > 0) {
    for (let i = 0; i < Math.min(filterCount, 2); i += 1) {
      const icon = filterIcons.nth(i);
      if (await icon.isVisible().catch(() => false)) {
        await icon.click({ timeout: 5000 }).catch(() => {});
        await page.waitForTimeout(500);
        filterShots.push(await screenshot(page, `async-task-filter-${i + 1}`));
        await page.keyboard.press('Escape').catch(() => {});
      }
    }
  }

  const resultTask = tasks.find((task) =>
    (task.task_status === 'success' || task.task_status === 'failed') &&
    (task.task_type === 'gremlin' || task.task_type === 'algorithm')
  );
  const result = {
    task_count: tasks.length,
    opened_task_result: false,
    skipped_task_result_reason: ''
  };

  if (resultTask) {
    await gotoPage(page, `/graph-management/${connId}/async-tasks/${resultTask.id}/result`);
    await page.waitForTimeout(2500);
    text = await visibleText(page);
    if (text.length < 2) {
      throw new Error(`async task result rendered too little visible text for task ${resultTask.id}`);
    }
    shots.push(await screenshot(page, 'async-task-result'));
    result.opened_task_result = true;
    result.task_result_id = resultTask.id;
  } else {
    result.skipped_task_result_reason = 'No completed gremlin/algorithm task exists in current Hubble metadata';
  }

  report.checks.async_tasks = {
    ...result,
    filter_popups_checked: filterShots.length,
    text_sample: trimText(text),
    screenshots: shots.concat(filterShots)
  };
}

async function responsiveAndI18nAcceptance(report, page, connId) {
  await page.setViewportSize({ width: 390, height: 844 });
  await gotoPage(page, `/graph-management/${connId}/data-analyze`);
  const mobileText = await expectText(page, /中文|English|Gremlin|数据分析|Data analysis/, 'mobile data analyze did not render');
  const mobileShot = await screenshot(page, 'responsive-mobile-data-analyze');

  await page.setViewportSize({ width: 1440, height: 960 });
  await gotoPage(page, '/');
  const text = await expectText(page, /中文|English|图管理|Graph Manager/i, 'desktop app bar/i18n control did not render');
  const desktopShot = await screenshot(page, 'responsive-desktop-home');

  const originalLanguage = await page.evaluate(() => localStorage.getItem('languageType') || 'zh-CN');
  await page.evaluate(() => localStorage.setItem('languageType', 'en-US'));
  await page.reload({ waitUntil: 'domcontentloaded' });
  await waitRoot(page);
  await page.waitForTimeout(1500);
  const enText = await expectText(page, /Graph Manager|Create Graph|English|Graph ID/i, 'English language render did not work');
  const enShot = await screenshot(page, 'i18n-english-home');
  await page.evaluate((language) => localStorage.setItem('languageType', language), originalLanguage);
  await page.reload({ waitUntil: 'domcontentloaded' });
  await waitRoot(page);
  await page.waitForTimeout(1000);

  report.checks.responsive_i18n = {
    mobile_viewport: '390x844',
    desktop_viewport: '1440x960',
    english_switch_restored_to: originalLanguage,
    text_sample: trimText(`${mobileText} ${text} ${enText}`),
    screenshots: [mobileShot, desktopShot, enShot]
  };
}

function normalizeErrors(consoleErrors, pageErrors, failedRequests) {
  const severeConsoleErrors = consoleErrors.filter((message) => {
    return !/favicon|ResizeObserver loop limit exceeded|Warning: componentWillReceiveProps/i.test(message);
  });
  const severeFailedRequests = failedRequests.filter((request) => {
    return !/favicon/i.test(request.url);
  });
  return { severeConsoleErrors, pageErrors, severeFailedRequests };
}

async function main() {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
  const report = {
    started_at: new Date().toISOString(),
    status: 'FAILED',
    browser: 'chromium',
    hubble_url: HUBBLE,
    checks: {},
    coverage: [
      'graph management search/create/edit/visit',
      'data analyze gremlin query and algorithm form',
      'sidebar navigation expand/collapse and main route jumps',
      'metadata list tabs/create entries/graph mode',
      'data import list/create modal/job details when available',
      'async task list/search/result when available',
      'desktop and mobile rendering',
      'console/page/request error collection'
    ]
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

    const connectionsPayload = await getApiJson(
      page.request,
      `${API}?page_no=1&page_size=20`
    );
    const connections = connectionsPayload.data.records || [];
    if (connections.length === 0) {
      throw new Error('No graph connection exists; full UI acceptance needs at least one connection');
    }
    const conn = connections.find((item) => item.enabled) || connections[0];
    const connId = conn.id;
    report.checks.connection_snapshot = {
      count: connections.length,
      selected: {
        id: conn.id,
        name: conn.name,
        graph: conn.graph,
        host: conn.host,
        port: conn.port,
        enabled: conn.enabled
      }
    };

    const [jobsPayload, tasksPayload, propertyKeys, vertexLabels, edgeLabels, propertyIndexes] =
      await Promise.all([
        getApiJson(page.request, `${API}/${connId}/job-manager?page_no=1&page_size=10`),
        getApiJson(page.request, `${API}/${connId}/async-tasks?page_no=1&page_size=10&type=&status=`),
        getApiJson(page.request, `${API}/${connId}/schema/propertykeys?page_no=1&page_size=10`),
        getApiJson(page.request, `${API}/${connId}/schema/vertexlabels?page_no=1&page_size=10`),
        getApiJson(page.request, `${API}/${connId}/schema/edgelabels?page_no=1&page_size=10`),
        getApiJson(page.request, `${API}/${connId}/schema/propertyindexes?page_no=1&page_size=10&is_vertex_label=true`)
      ]);

    report.checks.backend_snapshots = {
      import_jobs: jobsPayload.data.total,
      async_tasks: tasksPayload.data.total,
      property_keys: propertyKeys.data.total,
      vertex_labels: vertexLabels.data.total,
      edge_labels: edgeLabels.data.total,
      vertex_property_indexes: propertyIndexes.data.total
    };

    await graphManagementAcceptance(report, page, conn);
    await sidebarAcceptance(report, page, connId);
    await dataAnalyzeAcceptance(report, page, connId);
    await metadataAcceptance(report, page, connId);
    await importAcceptance(report, page, connId, jobsPayload.data.records || []);
    await asyncTasksAcceptance(report, page, connId, tasksPayload.data.records || []);
    await responsiveAndI18nAcceptance(report, page, connId);

    const { severeConsoleErrors, pageErrors: severePageErrors, severeFailedRequests } =
      normalizeErrors(consoleErrors, pageErrors, failedRequests);
    report.checks.console_errors = consoleErrors;
    report.checks.page_errors = pageErrors;
    report.checks.failed_requests = failedRequests;
    report.checks.severe_console_errors = severeConsoleErrors;
    report.checks.severe_failed_requests = severeFailedRequests;

    if (severeConsoleErrors.length > 0 || severePageErrors.length > 0 || severeFailedRequests.length > 0) {
      throw new Error(
        `UI acceptance saw browser errors: console=${severeConsoleErrors.length}, ` +
        `page=${severePageErrors.length}, requests=${severeFailedRequests.length}`
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

    const jsonPath = path.join(OUT_DIR, 'hubble_ui_full_acceptance_result.json');
    const mdPath = path.join(OUT_DIR, 'hubble_ui_full_acceptance_result.md');
    fs.writeFileSync(jsonPath, `${JSON.stringify(report, null, 2)}\n`);

    const snapshots = report.checks.backend_snapshots || {};
    const graph = report.checks.connection_snapshot || {};
    const lines = [
      '### UI Full Acceptance - 2026-06-09',
      '',
      `- Status: \`${report.status}\``,
      `- Browser: \`${report.browser}\``,
      `- Hubble URL: \`${report.hubble_url}\``,
      `- Selected graph connection: \`${graph.selected ? `${graph.selected.id}/${graph.selected.name}/${graph.selected.graph}` : ''}\``,
      `- Backend snapshots: import jobs=\`${snapshots.import_jobs}\`, async tasks=\`${snapshots.async_tasks}\`, property keys=\`${snapshots.property_keys}\`, vertex labels=\`${snapshots.vertex_labels}\`, edge labels=\`${snapshots.edge_labels}\`, vertex indexes=\`${snapshots.vertex_property_indexes}\``,
      `- Covered areas: \`${report.coverage.join('; ')}\``,
      `- Console errors: \`${(report.checks.console_errors || []).length}\``,
      `- Page errors: \`${(report.checks.page_errors || []).length}\``,
      `- Failed requests: \`${(report.checks.failed_requests || []).length}\``,
      `- Severe console errors: \`${(report.checks.severe_console_errors || []).length}\``,
      `- Severe failed requests: \`${(report.checks.severe_failed_requests || []).length}\``,
      report.checks.data_import && report.checks.data_import.skipped_job_details_reason
        ? `- Import detail skipped: \`${report.checks.data_import.skipped_job_details_reason}\``
        : '',
      report.checks.async_tasks && report.checks.async_tasks.skipped_task_result_reason
        ? `- Async result skipped: \`${report.checks.async_tasks.skipped_task_result_reason}\``
        : '',
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
