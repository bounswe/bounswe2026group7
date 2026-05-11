import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.js',
    // Vitest's default include matches `**/*.spec.{js,ts,jsx,tsx}`, which would
    // pick up the Playwright suite under `e2e/` and crash with
    // "test.describe.configure() not expected here" because those specs use
    // the @playwright/test runner. Keep the two test stacks separate.
    exclude: ['node_modules', 'dist', 'e2e/**'],
  },
  server: {
    port: 8000,
    allowedHosts: ['mymentornet.org', 'www.mymentornet.org'],
    proxy: {
      '/api': {
        target: process.env.VITE_BACKEND_URL || 'http://localhost:8080',
        changeOrigin: true,
      },
      // STOMP-over-WebSocket endpoint. Without `ws: true` Vite would 404 the
      // upgrade request and `useConversationSubscription` would never reach
      // the broker — AT-06 mentor↔mentee live-update fails as a result.
      '/ws/chat': {
        target: (process.env.VITE_BACKEND_URL || 'http://localhost:8080')
          .replace(/^http/, 'ws'),
        ws: true,
        changeOrigin: true,
      },
    },
  },
})
