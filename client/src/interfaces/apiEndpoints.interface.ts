export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export default interface ApiRequestConfig {
  url: string | ((param: string) => string);
  method: HttpMethod;
}
