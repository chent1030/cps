import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
// Arco Design 2.x 的静态方法（Message / Notification / Modal.confirm）内部仍从 'react-dom'
// 读取 createRoot/render；React 19 已移除这两个导出，缺了适配器会抛
// "copyRender is not a function"，表现为点删除按钮没反应。此文件由 Arco 官方提供，
// 把 createRoot 从 react-dom/client 注入回去，必须在渲染任何组件前加载。
import '@arco-design/web-react/es/_util/react-19-adapter'
import '@arco-design/web-react/dist/css/arco.css'
import './index.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
