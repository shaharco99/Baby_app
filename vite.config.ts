import path from 'node:path'
import { defineConfig } from 'vite'
import tailwindcss from '@tailwindcss/vite'
import { VitePWA } from 'vite-plugin-pwa'
import react, { reactCompilerPreset } from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'

// GitHub Pages project site: https://shaharco99.github.io/Baby_app/
const base = '/Baby_app/'

export default defineConfig({
  base,
  plugins: [
    react(),
    babel({ presets: [reactCompilerPreset()] }),
    tailwindcss(),
    VitePWA({
      registerType: 'prompt',
      includeAssets: ['favicon.svg', 'logo.svg'],
      manifest: {
        name: 'אור ירח',
        short_name: 'אור ירח',
        description: 'אור ירח – הכנות לקראת ההגעה של הבת של שחר וטופז',
        theme_color: '#292540',
        background_color: '#292540',
        display: 'standalone',
        scope: base,
        start_url: base,
        lang: 'he',
        dir: 'rtl',
        icons: [
          { src: 'logo.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any' },
          { src: 'logo.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'maskable' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2}'],
        cleanupOutdatedCaches: true,
        navigateFallback: `${base}index.html`,
        runtimeCaching: [
          {
            urlPattern: ({ request }) => request.destination === 'image',
            handler: 'CacheFirst',
            options: {
              cacheName: 'images',
              expiration: { maxEntries: 60, maxAgeSeconds: 60 * 60 * 24 * 30 },
            },
          },
        ],
      },
      devOptions: { enabled: false },
    }),
  ],
  resolve: {
    alias: { '@': path.resolve(import.meta.dirname, './src') },
  },
  build: {
    sourcemap: false,
    target: 'es2022',
  },
})
