import { httpClient } from './makeApiRequest';
import type { UiLang } from '@utils/speech';

/** Fetch Indian-voice MP3 from the backend (en-IN Neerja / kn-IN Sapna). */
export const fetchTtsAudio = async (
  text: string,
  lang: UiLang
): Promise<Blob> => {
  const { data } = await httpClient.post<Blob>(
    '/api/chat/tts',
    { text, lang },
    { responseType: 'blob' }
  );
  return data;
};
