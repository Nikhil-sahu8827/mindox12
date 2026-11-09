const { Innertube } = require('youtubei.js');

(async () => {
  try {
    console.log("Initializing Innertube with generate_session_locally: true...");
    const youtube = await Innertube.create({
      generate_session_locally: true
    });
    
    const videoId = "IsC0Ms2iFi8";
    console.log("Fetching info for:", videoId);
    const info = await youtube.getInfo(videoId);
    
    console.log("Fetching transcript...");
    const transcriptData = await info.getTranscript();
    const segments = transcriptData?.transcript?.content?.body?.initial_segments;
    console.log("Segments count:", segments ? segments.length : 0);
    if (segments && segments.length > 0) {
      console.log("First segment:", segments[0]?.snippet?.text);
      console.log("Last segment:", segments[segments.length - 1]?.snippet?.text);
    }
  } catch (err) {
    console.error("Error:", err.message, err.info);
  }
})();
