import { httpClient } from '@apiCalls/makeApiRequest';

/**
 * Empty-screen starter-prompt suggestions. `list` serves the rotating pool (personalized for the
 * signed-in user when they have generated rows); `generate` (re)builds them from the assistant's
 * info and, optionally, the user's memories + recent chats.
 */
export const suggestionsApi = {
  list: async (assistantId: string, lang: string): Promise<string[]> => {
    const { data } = await httpClient.get<string[]>(
      '/api/assistants/prompt-suggestions',
      { params: { assistantId, lang } }
    );
    return Array.isArray(data) ? data : [];
  },

  generate: async (
    assistantId: string,
    lang: string,
    personalized = true
  ): Promise<string[]> => {
    const { data } = await httpClient.post<string[]>(
      '/api/assistants/prompt-suggestions/generate',
      null,
      { params: { assistantId, lang, personalized } }
    );
    return Array.isArray(data) ? data : [];
  },
};
