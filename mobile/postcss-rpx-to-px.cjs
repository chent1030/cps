module.exports = function rpxToPx(options = {}) {
  const ratio = options.ratio || 2
  const unitPattern = /(-?\d*\.?\d+)rpx\b/g

  return {
    postcssPlugin: 'postcss-rpx-to-px',
    Declaration(decl) {
      if (!decl.value || !decl.value.includes('rpx')) return

      decl.value = decl.value.replace(unitPattern, (_, rawValue) => {
        const value = Number(rawValue)
        if (!Number.isFinite(value)) return `${rawValue}rpx`
        return `${Number((value / ratio).toFixed(4))}px`
      })
    },
  }
}

module.exports.postcss = true
