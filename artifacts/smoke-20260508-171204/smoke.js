const fs = require('fs');
const path = require('path');
const { chromium } = require('E:/Whd/USCDIP/frontend/node_modules/playwright');

const ART = 'E:/Whd/USCDIP/artifacts/smoke-20260508-171204';
const FRONT = 'http://localhost:5173';
const API = 'http://localhost:8080';
const logs = { startedAt: new Date().toISOString(), steps: [], console: [], pageErrors: [], network: [], api: [], websockets: [] };

function out(name) { return path.join(ART, name); }
function addStep(name, detail = {}) { logs.steps.push({ time: new Date().toISOString(), name, ...detail }); }
function compactBody(text) { return (text || '').slice(0, 2000); }
async function screenshot(page, name) { await page.screenshot({ path: out(name), fullPage: true }); addStep('screenshot', { name }); }
async function apiCall(context, method, url, token, body) {
  const started = Date.now();
  const response = await context.request.fetch(`${API}${url}`, {
    method,
    headers: { Accept: 'application/json', 'Content-Type': 'application/json', Authorization: `Bearer ${token}`, 'X-Trace-Id': `TRACE-SMOKE-${Date.now()}` },
    data: body
  });
  const text = await response.text();
  let json = null;
  try { json = JSON.parse(text); } catch {}
  const entry = { method, url, status: response.status(), ms: Date.now() - started, traceId: json?.traceId, success: json?.success, error: json?.error, dataSummary: summarize(json?.data), body: compactBody(text) };
  logs.api.push(entry);
  return json;
}
function summarize(data) {
  if (!data) return data;
  if (Array.isArray(data)) return { type: 'array', length: data.length, first: data[0] };
  if (data.items) return { total: data.total, page: data.page, pageSize: data.pageSize, first: data.items[0] };
  if (data.records) return { sourceBatchId: data.sourceBatchId || data.batchId, recordCount: data.records.length, firstRecord: data.records[0], status: data.status };
  return Object.fromEntries(Object.entries(data).slice(0, 12));
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, ignoreHTTPSErrors: true });
  const page = await context.newPage();

  page.on('console', msg => logs.console.push({ time: new Date().toISOString(), type: msg.type(), text: msg.text(), location: msg.location() }));
  page.on('pageerror', err => logs.pageErrors.push({ time: new Date().toISOString(), message: err.message, stack: err.stack }));
  page.on('request', req => {
    const url = req.url();
    if (url.includes('localhost') || url.includes('127.0.0.1')) logs.network.push({ time: new Date().toISOString(), type: 'request', method: req.method(), url, resourceType: req.resourceType() });
  });
  page.on('response', res => {
    const url = res.url();
    if (url.includes('localhost') || url.includes('127.0.0.1')) logs.network.push({ time: new Date().toISOString(), type: 'response', status: res.status(), url });
  });
  page.on('websocket', ws => {
    const entry = { time: new Date().toISOString(), url: ws.url(), frames: [] };
    logs.websockets.push(entry);
    ws.on('framesent', data => entry.frames.push({ dir: 'sent', time: new Date().toISOString(), payload: compactBody(data.payload) }));
    ws.on('framereceived', data => entry.frames.push({ dir: 'received', time: new Date().toISOString(), payload: compactBody(data.payload) }));
    ws.on('close', () => entry.closedAt = new Date().toISOString());
  });

  await page.goto(`${FRONT}/portal`, { waitUntil: 'networkidle' });
  addStep('portal_loaded', { url: page.url() });
  await page.getByRole('button', { name: '紧急登录' }).click();
  await page.locator('#emergency-username').fill('admin');
  await page.locator('#emergency-password').fill('Smoke#2026');
  await page.getByRole('button', { name: /^登录$/ }).click();
  await page.waitForFunction(() => sessionStorage.getItem('uscdip.accessToken'), null, { timeout: 15000 });
  await page.waitForLoadState('networkidle');
  await screenshot(page, '01-portal-login.png');
  const token = await page.evaluate(() => sessionStorage.getItem('uscdip.accessToken'));
  const userId = await page.evaluate(() => document.body.innerText.includes('U-ADMIN-001') ? 'U-ADMIN-001' : 'unknown');
  addStep('logged_in', { userId, tokenPresent: Boolean(token) });

  const now = new Date();
  const pad = n => String(n).padStart(2, '0');
  const baseTime = `${now.getFullYear()}-${pad(now.getMonth()+1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
  const trace = `TRACE-SMOKE-INGEST-${Date.now()}`;
  const ingest = await apiCall(context, 'POST', '/api/ingest/metrics', token, {
    protocolType: 'MQTT',
    sourceType: 'EDGE_GATEWAY',
    sourceKey: 'EDGE-SMOKE-CHROME',
    traceId: trace,
    isBackfill: false,
    metrics: [
      { deviceId: 'DEV-001', metricCode: 'PRESSURE', value: 0.94, eventTime: baseTime, recvTime: baseTime, deviceTime: baseTime, attributes: { topic: 'region/hz/dev-001/pressure', smoke: true } },
      { deviceId: 'DEV-001', metricCode: 'TEMPERATURE', value: 20.3, eventTime: baseTime, recvTime: baseTime, deviceTime: baseTime, attributes: { topic: 'region/hz/dev-001/temp', smoke: true } }
    ]
  });
  const batchId = ingest?.data?.batchId || ingest?.data?.sourceBatchId;
  const recordIds = (ingest?.data?.records || []).map(r => r.sourceRecordId || r.recordId).filter(Boolean);
  addStep('ingested_metrics', { batchId, recordIds, trace });

  if (batchId) await apiCall(context, 'GET', `/api/dq/scores?page=1&pageSize=20&sourceBatchId=${encodeURIComponent(batchId)}`, token);
  await apiCall(context, 'GET', '/api/alerts?page=1&pageSize=10&deviceId=DEV-001', token);
  await apiCall(context, 'GET', '/api/incidents?page=1&pageSize=10&deviceId=DEV-001', token);
  await apiCall(context, 'GET', '/api/workorders?page=1&pageSize=10&incidentId=INC-ALERT-SEED-001', token);
  await apiCall(context, 'GET', '/api/notifications?page=1&pageSize=10', token);

  await page.goto(`${FRONT}/mgmt/trends?deviceId=DEV-001&metricCode=PRESSURE`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1500);
  await screenshot(page, '02-dq-trends.png');

  await page.goto(`${FRONT}/mgmt/alerts`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(3500);
  await screenshot(page, '03-alerts-websocket-page.png');

  const directWs = await page.evaluate(async (token) => {
    const events = [];
    const push = (type, payload) => events.push({ at: new Date().toISOString(), type, payload: String(payload).slice(0, 1000) });
    await new Promise((resolve) => {
      const ws = new WebSocket(`ws://localhost:8080/ws/push?access_token=${encodeURIComponent(token)}`);
      let subscribed = false;
      const timer = setTimeout(() => { try { ws.close(); } catch {} resolve(); }, 5000);
      ws.onopen = () => { push('open', 'direct backend websocket opened'); ws.send('CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n\0'); };
      ws.onerror = () => push('error', 'direct backend websocket error');
      ws.onclose = (ev) => { push('close', `${ev.code} ${ev.reason}`); clearTimeout(timer); resolve(); };
      ws.onmessage = (ev) => {
        push('message', ev.data);
        if (!subscribed && String(ev.data).includes('CONNECTED')) {
          subscribed = true;
          ws.send('SUBSCRIBE\nid:sub-region\ndestination:/topic/region.REGION-HZ.workorder.notifications\n\n\0');
          ws.send('SEND\ndestination:/app/push/heartbeat\ncontent-type:application/json\n\n{"clientTime":"' + new Date().toISOString() + '"}\0');
          ws.send('SEND\ndestination:/app/push/ack\ncontent-type:application/json\n\n{"topic":"region.REGION-HZ.workorder.notifications","lastAckSeq":0}\0');
        }
      };
    });
    return events;
  }, token);
  logs.websockets.push({ time: new Date().toISOString(), url: 'direct:ws://localhost:8080/ws/push', frames: directWs });
  addStep('direct_websocket_probe', { events: directWs.length, first: directWs[0], last: directWs[directWs.length - 1] });

  await page.goto(`${FRONT}/mgmt/incidents/INC-ALERT-SEED-001`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1200);
  await screenshot(page, '04-incident-detail.png');

  await page.goto(`${FRONT}/emgc/workorders/WO-B22-CREATED-001`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1200);
  await screenshot(page, '05-workorder-detail.png');

  await page.goto(`${FRONT}/portal`, { waitUntil: 'networkidle' });
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
  await page.waitForTimeout(800);
  await screenshot(page, '06-notifications-portal.png');

  logs.finishedAt = new Date().toISOString();
  fs.writeFileSync(out('smoke-log.json'), JSON.stringify(logs, null, 2));
  await browser.close();
})().catch(async (err) => {
  logs.failedAt = new Date().toISOString();
  logs.error = { message: err.message, stack: err.stack };
  fs.writeFileSync(out('smoke-log.json'), JSON.stringify(logs, null, 2));
  console.error(err);
  process.exit(1);
});
