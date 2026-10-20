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

import { theme } from './theme'

dayjs.locale('zh-cn')
</script>
