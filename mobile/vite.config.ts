import { fileURLToPath, URL } from 'node:url'

import uniPlugin from '@dcloudio/vite-plugin-uni'
import { defineConfig } from 'vitest/config'

const uni = typeof uniPlugin === 'function' ? uniPlugin : (uniPlugin as unknown as { default: typeof uniPlugin }).default

export default defineConfig({
  plugins: [uni()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@vant/use': fileURLToPath(
        new URL('./node_modules/.pnpm/@vant+use@1.6.0_vue@3.5.38_typescript@5.9.3_/node_modules/@vant/use', import.meta.url),
      ),
      '@vant/popperjs': fileURLToPath(new URL('./node_modules/.pnpm/@vant+popperjs@1.3.0/node_modules/@vant/popperjs', import.meta.url)),
      '@vue/shared': fileURLToPath(new URL('./node_modules/.pnpm/@vue+shared@3.5.38/node_modules/@vue/shared', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
  },
})
