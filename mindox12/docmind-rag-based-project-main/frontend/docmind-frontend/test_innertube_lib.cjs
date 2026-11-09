const { Innertube } = require('youtubei.js');

(async () => {
  try {
    console.log("Initializing Innertube...");
    const youtube = await Innertube.create();
    
    const videoId = "IsC0Ms2iFi8"; // The 1h55m Java Lambdas video
    console.log("Fetching info for:", videoId);
    const info = await youtube.getInfo(videoId);
    
    console.log("Title:", info.basic_info.title);
    console.log("Author:", info.basic_info.author);
    console.log("Duration:", info.basic_info.duration);
    console.log("Views:", info.basic_info.view_count);
    
    console.log("Fetching transcript...");
    const transcriptData = await info.getTranscript();
    const segments = transcriptData?.transcript?.content?.body?.initial_segments;
    console.log("Segments count:", segments ? segments.length : 0);
    
    if (segments && segments.length > 0) {
      console.log("First segment:", segments[0]?.snippet?.text, "start_ms:", segments[0]?.start_ms);
      console.log("50th segment:", segments[49]?.snippet?.text, "start_ms:", segments[49]?.start_ms);
      console.log("Last segment:", segments[segments.length - 1]?.snippet?.text, "start_ms:", segments[segments.length - 1]?.start_ms);
    }
  } catch (err) {
    console.error("Error in test:", err);
  }
})();
