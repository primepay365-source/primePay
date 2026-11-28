import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '../services/apiClient'

/** Loads data from the API, re-running when `deps` change. Gives loading/error states and a reload(). */
export function useApiData<T>(load: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const loader = useRef(load)
  loader.current = load
  const [tick, setTick] = useState(0)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    loader.current()
      .then((result) => {
        if (!cancelled) setData(result)
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof ApiError ? e.message : 'Something went wrong loading this page.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick])

  const reload = useCallback(() => setTick((t) => t + 1), [])
  return { data, error, loading, reload, setData }
}

export function errorMessage(error: unknown, fallback = 'Something went wrong. Nothing was changed.'): string {
  return error instanceof ApiError ? error.message : fallback
}
