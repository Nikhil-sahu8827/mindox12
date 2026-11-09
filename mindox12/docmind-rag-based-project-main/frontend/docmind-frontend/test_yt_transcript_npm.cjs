const { YoutubeTranscript } = require('youtube-transcript');

(async () => {
  const videoIds = ["dQw4w9WgXcQ", "jNQXAC9IVRw", "IsC0Ms2iFi8"];
  for (const id of videoIds) {
    try {
      console.log(`\n--- Fetching transcript for ${id} ---`);
      const transcript = await YoutubeTranscript.fetchTranscript(id);
      console.log(`Success for ${id}! Total items:`, transcript.length);
      if (transcript.length > 0) {
        console.log("First item:", transcript[0]);
        console.log("Last item:", transcript[transcript.length - 1]);
      }
    } catch (e) {
      console.log(`Failed for ${id}:`, e.message);
    }
  }
})();
