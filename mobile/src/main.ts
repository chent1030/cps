import { createSSRApp } from 'vue'

import App from './App.vue'
import Vant from 'vant'
import 'vant/lib/index.css'
import './styles.css'

export const createApp = () => {
  const app = createSSRApp(App)
  app.use(Vant)
  return { app }
}
