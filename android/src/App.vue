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
/* 全局样式统一在 src/styles.css（含 src/styles/uni-controls.css）中维护 */
</style>
