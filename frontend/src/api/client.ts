import axios, { AxiosError } from 'axios';
import type { ApiErrorBody } from './types';

const TOKEN_KEY = 'nagrik.token';

export const tokenStore = {
  get: (): string | null => {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set: (token: string) => {
    try {
      localStorage.setItem(TOKEN_KEY, token);
    } catch {
      /* storage unavailable: session lasts for this tab only */
    }
  },
  clear: () => {
    try {
      localStorage.removeItem(TOKEN_KEY);
    } catch {
      /* ignore */
    }
  },
};

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30_000,
});

api.interceptors.request.use((config) => {
  const token = tokenStore.get();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/** Fired when the server rejects the session so the app can sign the user out with a clear message. */
export const SESSION_EXPIRED_EVENT = 'nagrik:session-expired';

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorBody>) => {
    const status = error.response?.status;
    const url = error.config?.url ?? '';
    if (status === 401 && !url.includes('/api/auth/login') && tokenStore.get()) {
      tokenStore.clear();
      window.dispatchEvent(
        new CustomEvent(SESSION_EXPIRED_EVENT, {
          detail: error.response?.data?.message ?? 'Your session has expired. Please sign in again.',
        }),
      );
    }
    return Promise.reject(error);
  },
);

/** Turns any thrown value into a message that is safe and useful to show. */
export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as ApiErrorBody | undefined;
    if (body?.message) return body.message;
    if (error.code === 'ECONNABORTED') return 'The request timed out. Check your connection and try again.';
    if (!error.response) return 'Cannot reach the Nagrik Setu server. Check your connection and try again.';
    if (error.response.status === 413) return 'The selected photos are too large to upload together.';
  }
  return fallback;
}

export function fieldErrors(error: unknown): Record<string, string> {
  if (axios.isAxiosError(error)) {
    return (error.response?.data as ApiErrorBody | undefined)?.fieldErrors ?? {};
  }
  return {};
}

/** True for failures worth retrying: network errors, timeouts and temporary server problems. */
export function isRetryable(error: unknown): boolean {
  if (!axios.isAxiosError(error)) return false;
  const status = error.response?.status;
  return !error.response || status === 429 || status === 502 || status === 503 || status === 504;
}

/** Evidence URLs from the API are relative; prefix the API base when the API lives elsewhere. */
export function mediaUrl(path?: string): string | undefined {
  if (!path) return undefined;
  const base = import.meta.env.VITE_API_BASE_URL || '';
  return path.startsWith('http') ? path : `${base}${path}`;
}
