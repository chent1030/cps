import { getCpsMockResponse } from '@/api/mock/cpsMock'

type QueryValue = string | number | boolean | null | undefined

interface RequestOptions extends Omit<RequestInit, 'body'> {
  params?: Record<string, QueryValue>
  body?: unknown
}

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''
const ENABLE_MOCK_FALLBACK = import.meta.env.VITE_ENABLE_MOCK_FALLBACK !== 'false'

const ORIGIN = typeof window !== 'undefined' && window.location ? window.location.origin : ''

const buildUrl = (path: string, params?: Record<string, QueryValue>) => {
  // app-plus 下 window.location 可能为空，使用绝对 API_BASE；H5 下回退 origin
  const base = API_BASE || ORIGIN
  const url = new URL(`${base}${path}`, ORIGIN || 'http://localhost')
  Object.entries(params ?? {}).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== '') {
      url.searchParams.set(key, String(value))
    }
  })
  return url.toString()
}

const send = async <T>(path: string, options: RequestOptions = {}) => {
  const isFormData = options.body instanceof FormData
  const method = options.method ?? 'GET'
  const rawBody = options.body

  try {
    if (!isFormData && canUseUniRequest()) {
      return await sendWithUni<T>(path, options)
    }
    return await sendWithFetch<T>(path, options)
  } catch (error) {
    return fallbackOrThrow<T>(
      method,
      path,
      options.params,
      rawBody,
      error instanceof Error ? error.message : 'CPS API request failed',
    )
  }
}

const sendWithFetch = async <T>(path: string, options: RequestOptions) => {
  const isFormData = options.body instanceof FormData
  const method = options.method ?? 'GET'
  let body: BodyInit | undefined
  if (method !== 'GET') {
    body = isFormData ? (options.body as FormData) : JSON.stringify(options.body ?? {})
  }
  const response = await fetch(buildUrl(path, options.params), {
    ...options,
    body,
    headers: {
      ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
      ...options.headers,
    },
  })

  if (!response.ok) {
    return fallbackOrThrow<T>(
      method,
      path,
      options.params,
      options.body,
      `CPS API ${response.status}: ${response.statusText}`,
    )
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

const sendWithUni = <T>(path: string, options: RequestOptions) => {
  const method = options.method ?? 'GET'
  return new Promise((resolve: (value: T) => void, reject) => {
    uni.request({
      url: buildUrl(path, options.params),
      method: method as UniApp.RequestOptions['method'],
      data: method === 'GET' ? undefined : options.body ?? {},
      header: {
        'Content-Type': 'application/json',
        ...(options.headers as Record<string, string> | undefined),
      },
      success(response) {
        const statusCode = response.statusCode ?? 0
        if (statusCode < 200 || statusCode >= 300) {
          try {
            resolve(fallbackOrThrow<T>(method, path, options.params, options.body, `CPS API ${statusCode}`))
          } catch (error) {
            reject(error)
          }
          return
        }
        resolve(response.data as T)
      },
      fail(error) {
        try {
          resolve(
            fallbackOrThrow<T>(method, path, options.params, options.body, error.errMsg || 'CPS API request failed'),
          )
        } catch (fallbackError) {
          reject(fallbackError)
        }
      },
    })
  })
}

const canUseUniRequest = () => {
  return typeof uni !== 'undefined' && typeof uni.request === 'function'
}

const fallbackOrThrow = <T>(
  method: string,
  path: string,
  params: Record<string, QueryValue> | undefined,
  body: unknown,
  message: string,
) => {
  if (ENABLE_MOCK_FALLBACK) {
    const mock = getCpsMockResponse<T>({ method, path, params, body })
    if (mock !== undefined) {
      return mock
    }
  }
  throw new Error(message)
}

export const request = {
  get: <T>(path: string, options?: RequestOptions) => {
    return send<T>(path, { ...options, method: 'GET' })
  },
  post: <T>(path: string, body?: unknown, options?: RequestOptions) => {
    return send<T>(path, { ...options, method: 'POST', body: body ?? {} })
  },
}
