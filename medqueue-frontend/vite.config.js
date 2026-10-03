import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'http://localhost:8080', changeOrigin: true, ws: true },
    },
  },
  // sockjs-client's browser build reads `global.crypto` at module load time.
  // Vite does not provide Node's `global` in the browser by default.
  define: { global: 'globalThis' },
})
