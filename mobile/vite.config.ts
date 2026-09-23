import { fileURLToPath, URL } from 'node:url'
import { createRequire } from 'node:module'
import path from 'node:path'

import uniPlugin from '@dcloudio/vite-plugin-uni'
import { defineConfig } from 'vitest/config'

const uni = typeof uniPlugin === 'function' ? uniPlugin : (uniPlugin as unknown as { default: typeof uniPlugin }).default

// vitest 环境修复（根因见 uni-cli-shared/dist/hbx/alias.js 的 initModuleAlias）：
// uni-cli-shared 在 VITEST=1 时用 module-alias 全局把 `vue`/`vue/package.json` 指到
// @dcloudio/uni-h5-vue。本仓库的测试（@vue/test-utils + vant + 组件源码）全部按真实
// vue 编写；uni 的替换会造成“双 vue 实例”分裂——test-utils 挂载走真实 vue，
// 经 vite SSR 管道加载的组件走 uni-h5-vue，两套响应式互不相通，表现为
// ref 赋值后 DOM 不更新、resolveComponent 告警、列表恒为空；且 @vitejs/plugin-vue
// 的 require.resolve('vue/compiler-sfc') 被 `vue` 前缀别名劫持，vitest 启动即崩
// "Failed to resolve vue/compiler-sfc"。
// 修复（module-alias 按“后注册者优先”匹配，以下均注册在 uni 之后故优先生效）：
//   1) 'vue'/'vue/package.json' 钉回真实 vue 3.5.38（统一 Node require 侧实例）；
//   2) 'vue/compiler-sfc' 钉回真实 compiler-sfc（修 plugin-vue 启动崩溃）；
//   3) pinRealVuePlugin（enforce:'pre' 且排在 uni() 之前）：vite SSR 管道里裸 'vue'
//      导入统一解析到真实 vue 目录，与 Node require 侧同一实例。
// 非 vitest 环境全部跳过，uni build 路径零影响。
const isVitest = Boolean(process.env.VITEST)
const pinRealVuePlugins = []
if (isVitest) {
  const nodeRequire = createRequire(import.meta.url)
  const realVueDir = fileURLToPath(new URL('./node_modules/vue', import.meta.url))
  const realVueEntry = path.join(realVueDir, 'index.js')
  const realVuePkgJson = path.join(realVueDir, 'package.json')
  const realVueCompilerSfc = path.join(realVueDir, 'compiler-sfc', 'index.js')
  const vitePluginUniDir = path.dirname(nodeRequire.resolve('@dcloudio/vite-plugin-uni/package.json'))
  const uniCliSharedDir = path.dirname(nodeRequire.resolve('@dcloudio/uni-cli-shared/package.json', { paths: [vitePluginUniDir] }))
  const moduleAlias = nodeRequire(path.join(path.dirname(path.dirname(uniCliSharedDir)), 'module-alias')) as {
    addAlias: (alias: string, target: string) => void
  }
  moduleAlias.addAlias('vue', realVueDir)
  moduleAlias.addAlias('vue/package.json', realVuePkgJson)
  moduleAlias.addAlias('vue/compiler-sfc', realVueCompilerSfc)
  pinRealVuePlugins.push({
    name: 'cps-pin-real-vue-for-vitest',
    enforce: 'pre',
    resolveId(id: string) {
      if (id === 'vue') {
        return realVueEntry
      }
      if (id === 'vue/package.json') {
        return realVuePkgJson
      }
      if (id === 'vue/compiler-sfc') {
        return realVueCompilerSfc
      }
      return null
    },
  })
}

export default defineConfig({
  plugins: [...pinRealVuePlugins, uni()],
  server: {
    host: '127.0.0.1',
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        secure: false,
      },
    },
  },
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
