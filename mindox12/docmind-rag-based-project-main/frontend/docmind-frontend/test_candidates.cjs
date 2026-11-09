const apiKey = process.env.OPENAI_API_KEY || "your-gemini-api-key";

const candidates = [
  "gemini-flash-latest",
  "gemini-flash-lite-latest",
  "gemini-2.5-flash-lite",
  "gemini-3.1-flash-lite",
  "gemini-3.5-flash-lite",
  "gemini-3.6-flash",
  "gemini-3.7-flash"
];

(async () => {
  for (const c of candidates) {
    try {
      const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${c}:generateContent?key=${apiKey}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          contents: [{ parts: [{ text: "Hello, reply 'OK'" }] }]
        })
      });
      const data = await res.json();
      if (res.ok) {
        console.log(`>>> MODEL ${c} IS ACTIVE! Output:`, data.candidates?.[0]?.content?.parts?.[0]?.text?.trim());
      } else {
        console.log(`Model ${c} failed:`, data.error?.status);
      }
    } catch(e) {
      console.log(`Model ${c} error:`, e.message);
    }
  }
})();
