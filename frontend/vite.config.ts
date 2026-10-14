import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  return {
    plugins: [react()],
    server: {
      port: 5173,
      // In development the API is proxied, so the browser talks to a single origin.
      // 127.0.0.1 (not localhost): on Windows, Node may resolve localhost to IPv6 ::1 first.
      proxy: {
        '/api': { target: env.VITE_DEV_API_PROXY || 'http://127.0.0.1:8080', changeOrigin: true },
      },
    },
    test: { environment: 'node' },
  };
});
