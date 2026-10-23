import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The interface catalog lives in reference-service (it seeds the dictionary database) and is bundled as the offline
// fallback of the interface; the dev server may read it from there.
const CATALOG = fileURLToPath(new URL('../reference-service/src/main/resources/i18n/interface', import.meta.url))

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    fs: {
      allow: ['.', CATALOG]
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8082',
        changeOrigin: true
      }
    }
  }
})
