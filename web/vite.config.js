import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/user': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/shop': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/shop-type': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/blog': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/review': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/follow': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/voucher': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/voucher-order': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/upload': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/stats': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
