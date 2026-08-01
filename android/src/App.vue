<template>
  <a-config-provider :locale="zhCN" :theme="theme">
    <view>
      <slot />
    </view>
  </a-config-provider>
</template>

<script setup lang="ts">
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context'

dayjs.locale('zh-cn')

const theme: ThemeConfig = {
  token: {
    colorPrimary: '#14b8a6',
    colorSuccess: '#22c55e',
    colorWarning: '#f97316',
    colorError: '#ef4444',
    colorInfo: '#2563eb',
    borderRadius: 10,
    fontFamily:
      'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
  },
}
</script>
