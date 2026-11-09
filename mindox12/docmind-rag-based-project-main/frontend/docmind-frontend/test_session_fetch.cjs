const { Innertube } = require('youtubei.js');

(async () => {
  try {
    const youtube = await Innertube.create();
    const info = await youtube.getInfo("IsC0Ms2iFi8");
    const track = info.captions?.caption_tracks?.[0];
    if (track) {
      console.log("Fetching track using youtube.session.http.fetch...");
      const res = await youtube.session.http.fetch(track.base_url);
      const text = await res.text();
      console.log("Result status:", res.status, "len:", text.length);
      if (text.length > 0) {
        console.log("SUCCESS PREVIEW:\n", text.substring(0, 300));
      }
    }
  } catch (err) {
    console.error("Error:", err);
  }
})();
