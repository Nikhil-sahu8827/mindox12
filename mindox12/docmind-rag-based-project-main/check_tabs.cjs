const fs = require('fs');
const content = fs.readFileSync('frontend/docmind-frontend/dist/assets/index-WsI9Spll.js', 'utf8');

const idx = content.indexOf('getProgress:');
console.log("getProgress found at", idx);
if (idx !== -1) {
  console.log(content.substring(idx - 100, idx + 400));
}
