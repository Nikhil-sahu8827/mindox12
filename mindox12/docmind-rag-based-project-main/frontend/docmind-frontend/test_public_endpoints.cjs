(async () => {
  const videoId = "IsC0Ms2iFi8";
  
  // Test 1: youtubetranscript.com
  try {
    const res1 = await fetch(`https://youtubetranscript.com/?server_vid2=${videoId}`);
    const text1 = await res1.text();
    console.log("youtubetranscript.com:", res1.status, "len:", text1.length, "preview:", text1.substring(0, 100));
  } catch(e) {
    console.log("youtubetranscript.com error:", e.message);
  }

  // Test 2: tactiq
  try {
    const res2 = await fetch(`https://tactiq-apps-prod.cloudfunctions.net/youtube_transcript?video_id=${videoId}`);
    const text2 = await res2.text();
    console.log("tactiq:", res2.status, "len:", text2.length, "preview:", text2.substring(0, 100));
  } catch(e) {
    console.log("tactiq error:", e.message);
  }

  // Test 3: subsmagic or others
  try {
    const res3 = await fetch(`https://subsmagic-free-tools.fly.dev/api/youtube-transcription?url=https://www.youtube.com/watch?v=${videoId}`);
    const text3 = await res3.text();
    console.log("subsmagic:", res3.status, "len:", text3.length, "preview:", text3.substring(0, 100));
  } catch(e) {
    console.log("subsmagic error:", e.message);
  }
})();
