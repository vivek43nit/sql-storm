import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/fkblitz': {
        target: 'http://localhost:9044',
        changeOrigin: true,
      }
    }
  }
})
