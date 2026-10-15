import { config } from '@vue/test-utils'
import Vant from 'vant'

config.global.plugins = [Vant]

// uni-h5 内置组件（uni-view/uni-button/uni-text 等）的部分 computed 会读取全局
// __uniConfig.router/assets（如 getRealPath）。jsdom 单测没有 uni 应用启动流程，
// 缺失时会抛 ReferenceError 并中断组件的响应式更新。这里按 uni-h5 默认值补一个
// 最小桩，仅影响测试环境（uni build h5 时由编译产物注入真实配置）。
if ((globalThis as Record<string, unknown>).__uniConfig === undefined) {
  ;(globalThis as Record<string, unknown>).__uniConfig = {
    appId: '',
    appName: 'cps-mobile-test',
    appVersion: '',
    appVersionCode: '',
    compilerVersion: '',
    uniCompilerVersion: '',
    version: '',
    locale: 'zh-Hans',
    fallbackLocale: 'zh-Hans',
    router: { base: '/', assets: '/static/' },
    nvue: {},
    networkTimeout: { request: 60000, uploadFile: 60000, downloadFile: 60000 },
    debug: false,
  }
}
