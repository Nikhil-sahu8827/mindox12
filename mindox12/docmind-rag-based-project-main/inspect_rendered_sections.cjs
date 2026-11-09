const { spawn } = require('child_process');
const http = require('http');

async function run() {
  const chrome = spawn('C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe', [
    '--headless=new',
    '--remote-debugging-port=9222',
    '--window-size=1400,900',
    '--disable-gpu',
    '--no-sandbox',
    'http://localhost:3000/'
  ]);

  let targets = null;
  for (let i = 0; i < 20; i++) {
    await new Promise(r => setTimeout(r, 400));
    try {
      targets = await new Promise((resolve, reject) => {
        http.get('http://127.0.0.1:9222/json', res => {
          let data = '';
          res.on('data', chunk => data += chunk);
          res.on('end', () => { try { resolve(JSON.parse(data)); } catch (e) { reject(e); } });
        }).on('error', reject);
      });
      if (targets && targets.some(t => t.type === 'page')) break;
    } catch (e) {}
  }

  const pageTarget = targets.find(t => t.type === 'page' && t.url.includes('3000')) || targets.find(t => t.type === 'page');
  const ws = new WebSocket(pageTarget.webSocketDebuggerUrl);
  await new Promise(resolve => ws.onopen = resolve);

  let id = 1;
  function send(method, params = {}) {
    return new Promise(resolve => {
      const curId = id++;
      const handler = (event) => {
        const msg = JSON.parse(event.data);
        if (msg.id === curId) {
          ws.removeEventListener('message', handler);
          resolve(msg.result);
        }
      };
      ws.addEventListener('message', handler);
      ws.send(JSON.stringify({ id: curId, method, params }));
    });
  }

  await send('Page.enable');
  await send('Runtime.enable');

  await new Promise(r => setTimeout(r, 4000));

  // Click My Growth tab
  await send('Runtime.evaluate', {
    expression: `
      (() => {
        const btn = document.querySelector('button[title="My Growth"]');
        if (btn) btn.click();
      })()
    `
  });

  await new Promise(r => setTimeout(r, 3000));

  // Inspect the rendered text of the whole page, section by section!
  const sections = await send('Runtime.evaluate', {
    expression: `
      (() => {
        const res = {};
        const ids = ['sec-clock', 'sec-stats', 'sec-courses', 'sec-growth-topics', 'sec-planner', 'sec-analytics', 'sec-calendar', 'sec-coach', 'sec-achievements'];
        ids.forEach(id => {
          const el = document.getElementById(id);
          res[id] = el ? el.innerText.substring(0, 300) : 'NOT FOUND';
        });
        return res;
      })()
    `,
    returnByValue: true
  });
  console.log('RENDERED SECTIONS:', JSON.stringify(sections?.result?.value, null, 2));

  // Also check if any console errors were thrown
  const logs = await send('Runtime.evaluate', {
    expression: `
      (() => {
        return {
          dashboardData: !!window.lastDashboardData,
          growthDataStatus: !!window.lastGrowthData
        };
      })()
    `,
    returnByValue: true
  });

  ws.close();
  chrome.kill();
}

run().catch(console.error);
