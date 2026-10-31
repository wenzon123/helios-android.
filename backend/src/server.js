import 'dotenv/config';
import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import rateLimit from 'express-rate-limit';

const app = express();
app.use(helmet());
app.use(express.json({ limit: '1mb' }));
const origins = (process.env.ALLOWED_ORIGINS || '').split(',').map(x => x.trim()).filter(Boolean);
app.use(cors({ origin: origins.length ? origins : false }));
app.use('/v1/', rateLimit({ windowMs: 60_000, limit: 20, standardHeaders: true, legacyHeaders: false }));

function normalizeMessages(messages = [], personality = '') {
  const incoming = Array.isArray(messages) ? messages : [];
  const safe = incoming.slice(-20).filter(m => ['user', 'assistant'].includes(m.role) && typeof m.content === 'string');
  return [{ role: 'system', content: personality || 'Você é HÉLIOS, assistente em português brasileiro. Seja inteligente, objetivo, educado e transparente. Não afirme que executou ações no dispositivo: o aplicativo faz isso separadamente.' }, ...safe];
}
async function askGemini(messages) {
  if (!process.env.GEMINI_API_KEY) throw new Error('Gemini não configurado');
  const model = process.env.GEMINI_MODEL || 'gemini-2.5-flash';
  const system = messages.find(m => m.role === 'system')?.content || '';
  const contents = messages.filter(m => m.role !== 'system').map(m => ({ role: m.role === 'assistant' ? 'model' : 'user', parts: [{ text: m.content }] }));
  const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${process.env.GEMINI_API_KEY}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ systemInstruction: { parts: [{ text: system }] }, contents, generationConfig: { temperature: 0.7, maxOutputTokens: 900 } })
  });
  const data = await response.json();
  if (!response.ok) throw new Error(`Gemini ${response.status}: ${data.error?.message || 'falha no provedor'}`);
  const reply = data.candidates?.[0]?.content?.parts?.map(p => p.text || '').join('').trim();
  if (!reply) throw new Error('Gemini retornou resposta vazia');
  return reply;
}
async function askGroq(messages) {
  if (!process.env.GROQ_API_KEY) throw new Error('Groq não configurado');
  const model = process.env.GROQ_MODEL || 'llama-3.3-70b-versatile';
  const response = await fetch('https://api.groq.com/openai/v1/chat/completions', {
    method: 'POST', headers: { 'Authorization': `Bearer ${process.env.GROQ_API_KEY}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ model, messages, temperature: 0.7, max_tokens: 900 })
  });
  const data = await response.json();
  if (!response.ok) throw new Error(`Groq ${response.status}: ${data.error?.message || 'falha no provedor'}`);
  const reply = data.choices?.[0]?.message?.content?.trim();
  if (!reply) throw new Error('Groq retornou resposta vazia');
  return reply;
}
app.get('/health', (_req, res) => res.json({ ok: true, service: 'helios-backend', version: '0.2.0' }));
app.post('/v1/chat', async (req, res) => {
  const messages = normalizeMessages(req.body?.messages, req.body?.personality);
  if (messages.length < 2 || !messages.some(m => m.role === 'user')) return res.status(400).json({ error: 'Envie ao menos uma mensagem do usuário.' });
  try {
    const reply = await askGemini(messages);
    return res.json({ reply, provider: 'gemini' });
  } catch (primaryError) {
    if (process.env.GROQ_API_KEY) {
      try { const reply = await askGroq(messages); return res.json({ reply, provider: 'groq', fallback: true }); }
      catch (fallbackError) { return res.status(502).json({ error: 'Provedores indisponíveis', details: `${primaryError.message}; ${fallbackError.message}` }); }
    }
    return res.status(502).json({ error: 'Gemini indisponível ou não configurado', details: primaryError.message });
  }
});
const port = Number(process.env.PORT || 8787);
const host = process.env.HOST || '0.0.0.0';
app.listen(port, host, () => console.log(`HÉLIOS backend listening on http://${host}:${port}`));
