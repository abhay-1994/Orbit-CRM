import { useQuery } from '@tanstack/react-query'
import { useCallback, useEffect, useState } from 'react'
import { http } from '../api/client'
import type { Action, ModuleDetail, PipelineView } from '../api/types'
import { useMe } from '../auth/context'

export function useDebounced<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])
  return debounced
}

/** Field metadata of a record module; drives lists, forms and filters. */
export function useModuleMeta(module: string | undefined) {
  return useQuery({
    queryKey: ['meta', module],
    queryFn: () => http.get<ModuleDetail>(`/metadata/modules/${module}`),
    enabled: !!module,
    staleTime: 5 * 60_000,
  })
}

export function usePipelines() {
  return useQuery({
    queryKey: ['pipelines'],
    queryFn: () => http.get<PipelineView[]>('/pipelines'),
    staleTime: 5 * 60_000,
  })
}

/** Returns a check for "may the current user do this?". The server enforces the same rules. */
export function useCan(): (resource: string, action: Action) => boolean {
  const me = useMe()
  return useCallback(
    (resource: string, action: Action) => {
      const scope = me.permissions[resource]?.[action]
      return !!scope && scope !== 'NONE'
    },
    [me],
  )
}
