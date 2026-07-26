import makeApiRequest from './makeApiRequest';
import { API_ENDPOINTS } from '@constants/apiEndpoints';
import type { SemanticFactDto } from '@interfaces/memory.interface';

/** The current user's stored long-term memories, most-recently-used first. */
export const fetchMemories = (): Promise<SemanticFactDto[]> =>
  makeApiRequest<SemanticFactDto[]>({}, API_ENDPOINTS.LIST_MEMORIES);

/** Forget a single memory by id. */
export const deleteMemory = (id: string): Promise<void> =>
  makeApiRequest<void>({}, API_ENDPOINTS.DELETE_MEMORY, id);

/** Forget every memory the agent holds about the user. */
export const deleteAllMemories = (): Promise<void> =>
  makeApiRequest<void>({}, API_ENDPOINTS.DELETE_ALL_MEMORIES);
