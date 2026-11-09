const apiKey = process.env.OPENAI_API_KEY || "your-gemini-api-key";

(async () => {
  const models = ["gemini-flash-lite-latest", "gemini-3.1-flash-lite", "gemini-3.5-flash-lite"];
  for (const m of models) {
    for (let i = 0; i < 3; i++) {
      const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${m}:generateContent?key=${apiKey}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ contents: [{ parts: [{ text: "Respond 'SUCCESS'" }] }] })
      });
      const data = await res.json();
      console.log(`Model ${m} call ${i}:`, res.status, data.candidates?.[0]?.content?.parts?.[0]?.text?.trim() || data.error?.status);
    }
  }
})();
