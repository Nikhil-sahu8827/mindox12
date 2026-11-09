const apiKey = process.env.OPENAI_API_KEY || "your-gemini-api-key";

const models = [
  "gemini-2.5-flash",
  "gemini-2.0-flash",
  "gemini-1.5-flash",
  "gemini-2.0-flash-exp",
  "gemini-2.5-pro",
  "gemini-1.5-pro"
];

(async () => {
  for (const m of models) {
    try {
      const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${m}:generateContent?key=${apiKey}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          contents: [{ parts: [{ text: "Hello" }] }]
        })
      });
      const data = await res.json();
      if (res.ok) {
        console.log(`>>> MODEL ${m} WORKS!`);
      } else {
        console.log(`Model ${m} failed:`, data.error?.message || data.error?.status);
      }
    } catch (e) {
      console.log(`Model ${m} error:`, e.message);
    }
  }
})();
