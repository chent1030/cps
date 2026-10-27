<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'

// uni-app 的 App.vue 不参与视图渲染（模板会被忽略），只承载全局生命周期。
// 本项目不使用全局样式：每个页面的样式（含 uni 内置控件外观与 --cps-* 变量）
// 全部内联在各自的 <style scoped> 里，保证 App 端与 H5 表现一致。
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
/* 本项目不使用全局样式：样式由各页面 <style scoped> 自包含。 */
</style>
