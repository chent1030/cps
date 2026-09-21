import { useCallback, useEffect, useState } from 'react'
import { errorMessage } from '../lib/presentation'

interface RemoteState<T> {
  loading: boolean
  error: string | null
  data: T | null
  refresh: () => Promise<void>
}

export function useRemote<T>(load: () => Promise<T>, dependencies: unknown[] = []): RemoteState<T> {
  const [state, setState] = useState<Omit<RemoteState<T>, 'refresh'>>({ loading: true, error: null, data: null })
  const refresh = useCallback(async () => {
    setState((previous) => ({ ...previous, loading: true, error: null }))
    try {
      setState({ loading: false, error: null, data: await load() })
    } catch (error) {
      setState({ loading: false, error: errorMessage(error), data: null })
    }
    // load is intentionally driven by the explicit dependency list at each call site.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, dependencies)

  useEffect(() => { void refresh() }, [refresh])
  return { ...state, refresh }
}
