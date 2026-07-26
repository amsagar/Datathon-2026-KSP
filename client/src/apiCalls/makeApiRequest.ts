import axios, { AxiosResponse } from 'axios';
import type ApiRequestConfig from '@interfaces/apiEndpoints.interface';
import { getAuthToken, clearAuthToken, redirectToSso } from '@apiCalls/auth';

// No instance-level Content-Type: it would be merged onto FormData uploads,
// overriding the browser's multipart/form-data boundary. JSON requests set the
// header per-request below; FormData requests leave it unset on purpose.
export const httpClient = axios.create();

// Attach the SSO-minted JWT as a Bearer token on every request.
httpClient.interceptors.request.use((config) => {
  const token = getAuthToken();
  if (token) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

httpClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // Session expired / unauthorized — drop the stale token and go to login.
    // 403 = authenticated but role-forbidden (e.g. risk scores for a policymaker):
    // surfaced to the caller instead of logging the user out.
    const status = error?.response?.status;
    if (status === 401) {
      clearAuthToken();
      redirectToSso();
    } else {
      console.error('Error in API call:', error);
    }
    return Promise.reject(error);
  }
);

const isFormData = (payload: unknown): payload is FormData =>
  typeof FormData !== 'undefined' && payload instanceof FormData;

/**
 * Generic API call function. Mirrors mvt-v2's makeApiRequest, with two
 * additions: JSON payloads get an application/json Content-Type while FormData
 * payloads are left without one so the browser sets the multipart boundary, and
 * the return value is the response body (not the full Axios response) so callers
 * don't have to dig into `.data`.
 */
const makeApiRequest = async <T = unknown>(
  payload: unknown,
  requestConfig: ApiRequestConfig,
  parametersOrQuery: string = '',
  customHeaders: Record<string, string> = {}
): Promise<T> => {
  const url =
    typeof requestConfig.url === 'function'
      ? requestConfig.url(parametersOrQuery)
      : requestConfig.url;

  const headers: Record<string, string> = { ...customHeaders };
  if (!isFormData(payload) && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }

  let response: AxiosResponse<T>;
  switch (requestConfig.method) {
    case 'GET':
      response = await httpClient.get<T>(url, { headers });
      break;
    case 'POST':
      response = await httpClient.post<T>(url, payload, { headers });
      break;
    case 'PUT':
      response = await httpClient.put<T>(url, payload, { headers });
      break;
    case 'PATCH':
      response = await httpClient.patch<T>(url, payload, { headers });
      break;
    case 'DELETE':
      response = await httpClient.delete<T>(url, { headers });
      break;
    default:
      throw new Error(
        `Unsupported request method: ${requestConfig.method as string}`
      );
  }
  return response.data;
};

export default makeApiRequest;

// Tiny helper for url-search-string building so call sites stay declarative.
export const qs = (
  params: Record<string, string | number | boolean | undefined | null>
): string => {
  const pairs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(
      ([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`
    );
  return pairs.length ? `?${pairs.join('&')}` : '';
};
