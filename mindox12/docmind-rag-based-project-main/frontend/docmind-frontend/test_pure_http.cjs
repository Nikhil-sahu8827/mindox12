const https = require('https');

const postData = JSON.stringify({
  context: {
    client: {
      clientName: 'ANDROID',
      clientVersion: '20.10.38'
    }
  },
  videoId: 'IsC0Ms2iFi8'
});

const req = https.request('https://www.youtube.com/youtubei/v1/player?prettyPrint=false', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(postData),
    'User-Agent': 'com.google.android.youtube/20.10.38 (Linux; U; Android 14)'
  }
}, res => {
  let data = '';
  res.on('data', c => data += c);
  res.on('end', () => {
    console.log("Player status:", res.statusCode, "len:", data.length);
    try {
      const json = JSON.parse(data);
      const tracks = json.captions?.playerCaptionsTracklistRenderer?.captionTracks;
      console.log("Found caption tracks:", tracks ? tracks.length : 0);
      if (tracks && tracks.length > 0) {
        console.log("Track 0:", tracks[0].languageCode, tracks[0].baseUrl);
        https.get(tracks[0].baseUrl, {
          headers: {
            'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_4) AppleWebKit/537.36'
          }
        }, capRes => {
          let capData = '';
          capRes.on('data', d => capData += d);
          capRes.on('end', () => {
            console.log(">>> CAPTION FETCH SUCCESS! Status:", capRes.statusCode, "length:", capData.length);
            console.log("Preview:\n", capData.substring(0, 300));
          });
        });
      }
    } catch (e) {
      console.log("Parse err:", e.message);
    }
  });
});

req.on('error', e => console.error(e));
req.write(postData);
req.end();
