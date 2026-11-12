import { defineConfig } from 'vitest/config';

// Relative paths, so the built files also load from the Capacitor WebView.
export default defineConfig({
  base: './',
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
  test: {
    environment: 'node',
  },
});
