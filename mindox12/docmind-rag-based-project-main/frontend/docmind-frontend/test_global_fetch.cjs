const { Innertube } = require('youtubei.js');

(async () => {
  try {
    const youtube = await Innertube.create();
    const info = await youtube.getInfo("IsC0Ms2iFi8");
    const track = info.captions?.caption_tracks?.[0];
    if (track) {
      console.log("Fetching track using globalThis.fetch...");
      const res = await fetch(track.base_url, {
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
        }
      });
      const text = await res.text();
      console.log("Global fetch status:", res.status, "len:", text.length);
      if (text.length > 0) {
        console.log("GLOBAL FETCH SUCCESS PREVIEW:\n", text.substring(0, 300));
      }
    }
  } catch (err) {
    console.error("Error:", err);
  }
})();
