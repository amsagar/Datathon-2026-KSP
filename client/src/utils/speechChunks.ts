/** Split text into short speakable chunks so the first TTS request returns quickly. */
export const splitSpeechChunks = (text: string, maxLen = 220): string[] => {
  const cleaned = (text || '').replace(/\s+/g, ' ').trim();
  if (!cleaned) return [];

  const sentences = cleaned
    .split(/(?<=[.!?。؟!…\n])\s+/)
    .map((s) => s.trim())
    .filter(Boolean);

  const chunks: string[] = [];
  let buf = '';
  for (const sentence of sentences.length ? sentences : [cleaned]) {
    if (!buf) {
      buf = sentence;
      continue;
    }
    if (buf.length + 1 + sentence.length <= maxLen) {
      buf = `${buf} ${sentence}`;
    } else {
      chunks.push(buf);
      buf = sentence;
    }
  }
  if (buf) chunks.push(buf);

  // Hard-split any leftover oversized piece.
  const out: string[] = [];
  for (const c of chunks) {
    if (c.length <= maxLen) {
      out.push(c);
      continue;
    }
    let rest = c;
    while (rest.length > maxLen) {
      let cut = rest.lastIndexOf(' ', maxLen);
      if (cut < maxLen / 2) cut = maxLen;
      out.push(rest.slice(0, cut).trim());
      rest = rest.slice(cut).trim();
    }
    if (rest) out.push(rest);
  }
  return out;
};
