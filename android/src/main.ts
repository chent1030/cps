import { createSSRApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'

export const createApp = () => {
  const app = createSSRApp(App)
  app.use(createPinia())
  return { app }
}
