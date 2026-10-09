import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The dev server proxies API calls to Spring Boot, so the browser sees one origin (no CORS setup needed).
// Override the target with API_TARGET=http://host:port npm run dev
const apiTarget = process.env.API_TARGET ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': apiTarget,
      '/actuator': apiTarget,
    },
  },
})
