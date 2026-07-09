/// <reference types="vite/client" />

declare global {
  namespace JSX {
    interface Element {}
    interface IntrinsicElements {
      [elem: string]: unknown
    }
  }

  interface Window {
    uni?: UniApp.Uni
  }
}

export {}
