import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context'

/**
 * 全局品牌主题（ant-design-vue token）。
 * 与 styles.css 里的 --cps-* 令牌保持一致：主色用深青（#0f766e），
 * 白字对比度 4.9:1，满足 WCAG AA；圆角与控件高度与移动端一致。
 */
export const theme: ThemeConfig = {
  token: {
    colorPrimary: '#0f766e',
    colorInfo: '#0f766e',
    colorSuccess: '#16a34a',
    colorWarning: '#ea580c',
    colorError: '#dc2626',
    borderRadius: 10,
    controlHeight: 40,
    fontFamily:
      'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
  },
}
