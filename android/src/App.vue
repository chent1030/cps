<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'

// uni-app 的 App.vue 不参与视图渲染（模板会被忽略），只承载全局生命周期与全局样式。
// 主题色统一由 src/styles.css 的 --cps-* CSS 变量提供（原先的 antd config-provider 已移除）。
onLaunch(() => {
  console.log('[cps] app launch')
})

onShow(() => {
  console.log('[cps] app show')
})

onHide(() => {
  console.log('[cps] app hide')
})
</script>

<style>
/* App.vue 的 style 是 uni-app 在 App 平台（app-plus）的全局样式入口。
   main.ts 里 import 的 css 只保证 H5 生效；App 端若不在这里引入，
   .u-* 控件样式与 --cps-* 变量会整体丢失，页面看起来"没有样式"。 */
@import './styles.css';
</style>
