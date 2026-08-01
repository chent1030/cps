import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vitest/config'

// 测试环境（vitest）下，uni-app 的 module-alias 会把 `vue` 重定向到自带的
// uni-h5-vue，导致 @vitejs/plugin-vue 无法解析 vue/compiler-sfc。
// 我们的测试均为纯 TS / Pinia 逻辑（不依赖 uni 运行时），因此测试环境跳过 uni 插件，
// 直接使用真实 vue 与 vite 的 vue 插件即可。
const isTest = !!process.env.VITEST

async function buildPlugins() {
  if (isTest) {
    const vue = (await import('@vitejs/plugin-vue')).default
    return [vue()]
  }
  const uniPlugin = (await import('@dcloudio/vite-plugin-uni')).default
  const uni = typeof uniPlugin === 'function' ? uniPlugin : (uniPlugin as unknown as { default: typeof uniPlugin }).default
  return [uni()]
}

export default defineConfig(async () => {
  const plugins = await buildPlugins()
  return {
    plugins,
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
      dedupe: ['vue'],
    },
    test: {
      root: fileURLToPath(new URL('.', import.meta.url)),
      environment: 'jsdom',
      globals: true,
      setupFiles: './src/test/setup.ts',
    },
  }
})
