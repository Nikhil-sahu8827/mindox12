const apiKey = process.env.OPENAI_API_KEY || "your-gemini-api-key";

(async () => {
  try {
    const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models?key=${apiKey}`);
    const data = await res.json();
    console.log("Total models available:", data.models ? data.models.length : 0);
    if (data.models) {
      for (const m of data.models) {
        if (m.supportedGenerationMethods?.includes("generateContent")) {
          console.log(`- ${m.name} (${m.displayName})`);
        }
      }
    }
  } catch (e) {
    console.log("Error:", e.message);
  }
})();
