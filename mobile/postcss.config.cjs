const rpxToPx = require('./postcss-rpx-to-px.cjs')

module.exports = {
  plugins: [require('tailwindcss'), rpxToPx({ ratio: 2 }), require('autoprefixer')],
}
