const fs = require('fs');

// Create mock browser/react environment
global.window = {};
let renderedJsx = null;

const React = {
  useState: (initial) => [typeof initial === 'function' ? initial() : initial, () => {}],
  useEffect: (fn, deps) => {},
  useMemo: (fn, deps) => fn(),
  useRef: (initial) => ({ current: initial }),
  useCallback: (fn, deps) => fn
};

global.window.__DocmindReact = {
  React,
  jsx: (type, props) => ({ type, props }),
  jsxs: (type, props) => ({ type, props }),
  Fragment: 'Fragment',
  toast: { success: () => {}, error: () => {} },
  store: {},
  auth: { getState: () => ({ token: 'mock-token', user: { username: 'testuser' } }) }
};

// Now load growth-dashboard.js
const code = fs.readFileSync('frontend/docmind-frontend/dist/assets/growth-dashboard.js', 'utf8');
try {
  eval(code);
  console.log("growth-dashboard.js evaluated successfully!");
  console.log("window.GrowthDashboard type:", typeof global.window.GrowthDashboard);
  
  // Call the component function
  const res = global.window.GrowthDashboard({});
  console.log("GrowthDashboard returned successfully!");
  console.log("Root element type:", res?.type);
} catch (e) {
  console.error("ERROR during GrowthDashboard execution:", e);
}
