import { createSSRApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'

import App from './App.vue'
import 'ant-design-vue/dist/reset.css'
import './styles.css'

export const createApp = () => {
  const app = createSSRApp(App)
  app.use(createPinia())
  // 全局注册 ant-design-vue 组件（视图以 <a-button> 等全局写法使用，对应 mobile 的 app.use(Vant)）
  app.use(Antd)
  return { app }
}
