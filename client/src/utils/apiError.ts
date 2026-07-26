import axios, { type AxiosError } from 'axios';
import { ROUTE_PATHS } from '@constants/routePaths';

export interface ErrorPageDetails {
  status?: number;
  title: string;
  message: string;
  retryable: boolean;
}

export const getErrorDetails = (
  status?: number,
  customMessage?: string | null
): ErrorPageDetails => {
  if (customMessage) {
    return {
      status,
      title: 'Something went wrong',
      message: customMessage,
      retryable: true,
    };
  }

  switch (status) {
    case 504:
      return {
        status: 504,
        title: 'Gateway timeout',
        message:
          'The server took too long to respond. Please try again in a moment.',
        retryable: true,
      };
    case 503:
      return {
        status: 503,
        title: 'Service unavailable',
        message:
          'The application is temporarily unavailable. Please try again shortly.',
        retryable: true,
      };
    case 502:
      return {
        status: 502,
        title: 'Bad gateway',
        message:
          'We could not reach the application server. Please try again.',
        retryable: true,
      };
    case 500:
      return {
        status: 500,
        title: 'Server error',
        message: 'Something went wrong on our end. Please try again.',
        retryable: true,
      };
    case 408:
      return {
        status: 408,
        title: 'Request timed out',
        message: 'The request timed out before completing. Please try again.',
        retryable: true,
      };
    default:
      if (status === undefined) {
        return {
          title: 'Connection problem',
          message:
            'Unable to reach the server. Check your network connection and try again.',
          retryable: true,
        };
      }
      return {
        status,
        title: 'Something went wrong',
        message: 'An unexpected error occurred. Please try again.',
        retryable: true,
      };
  }
};

const readServerMessage = (error: AxiosError): string | undefined => {
  const data = error.response?.data;
  if (typeof data === 'string' && data.trim()) return data.trim();
  if (data && typeof data === 'object' && 'message' in data) {
    const message = (data as { message?: unknown }).message;
    if (typeof message === 'string' && message.trim()) return message.trim();
  }
  return undefined;
};

export const getErrorDetailsFromAxios = (error: unknown): ErrorPageDetails => {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    const serverMessage = readServerMessage(error);
    if (!error.response && error.code === 'ERR_NETWORK') {
      return getErrorDetails(undefined);
    }
    return getErrorDetails(status, serverMessage);
  }
  if (error instanceof Error && error.message) {
    return getErrorDetails(undefined, error.message);
  }
  return getErrorDetails();
};

export const shouldShowErrorPage = (status?: number): boolean => {
  if (status === undefined) return true;
  if (status >= 500) return true;
  return status === 408;
};

export const goToErrorPage = (
  details: Partial<ErrorPageDetails> & { status?: number } = {}
) => {
  if (typeof window === 'undefined') return;
  if (window.location.pathname === ROUTE_PATHS.ERROR) return;

  const params = new URLSearchParams();
  if (details.status) params.set('status', String(details.status));
  if (details.message) params.set('message', details.message);
  const query = params.toString();
  window.location.assign(`${ROUTE_PATHS.ERROR}${query ? `?${query}` : ''}`);
};

export const goToErrorPageFromAxios = (error: unknown) => {
  if (!axios.isAxiosError(error)) return;
  const status = error.response?.status;
  if (status === 401 || status === 403) return;
  if (!shouldShowErrorPage(status)) return;
  const details = getErrorDetailsFromAxios(error);
  goToErrorPage({ status, message: details.message });
};

export const registerGlobalApiErrorHandlers = () => {
  if (typeof window === 'undefined') return;

  window.addEventListener('unhandledrejection', (event) => {
    if (!axios.isAxiosError(event.reason)) return;
    if (!shouldShowErrorPage(event.reason.response?.status)) return;
    if (event.reason.response?.status === 401 || event.reason.response?.status === 403) {
      return;
    }
    event.preventDefault();
    goToErrorPageFromAxios(event.reason);
  });
};
