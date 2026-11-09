const { Innertube } = require('youtubei.js');

(async () => {
  try {
    const youtube = await Innertube.create();
    const info = await youtube.getInfo("IsC0Ms2iFi8");
    console.log("Captions property:", info.captions ? Object.keys(info.captions) : null);
    if (info.captions?.caption_tracks) {
      console.log("Tracks count:", info.captions.caption_tracks.length);
      for (const t of info.captions.caption_tracks) {
        console.log("Track:", t.language_code, t.kind, t.base_url);
      }
    }
  } catch (err) {
    console.error("Error:", err);
  }
})();
