import { useQuery } from '@tanstack/react-query'
import { Select } from 'antd'
import { useState, type CSSProperties } from 'react'
import { http, qs } from '../api/client'
import type { PageResponse, RecordView, UserLookup } from '../api/types'
import { useDebounced } from '../lib/hooks'

interface Option {
  value: string
  label: string
}

interface PickerProps<V> {
  id?: string
  value?: V
  onChange?: (value: V) => void
  /** Label of the current value, shown before the first search (e.g. the owner's name). */
  initialLabel?: string
  placeholder?: string
  multiple?: boolean
  disabled?: boolean
  style?: CSSProperties
}

/**
 * Remote-search select that remembers the labels of picked options, so a selected value keeps its
 * label when a later search no longer returns it.
 */
function RemoteSelect<V extends string | string[]>({
  id,
  value,
  onChange,
  initialLabel,
  placeholder,
  multiple,
  disabled,
  style,
  options,
  loading,
  onSearch,
  extra = [],
}: PickerProps<V> & { options: Option[]; loading: boolean; onSearch: (q: string) => void; extra?: Option[] }) {
  const [picked, setPicked] = useState<Record<string, string>>({})

  const selected = (Array.isArray(value) ? value : value ? [value] : []).filter(
    (v) => !options.some((o) => o.value === v) && !extra.some((o) => o.value === v),
  )
  const fallback = (v: string) => picked[v] ?? (typeof value === 'string' ? initialLabel : undefined) ?? v
  const all = [...extra, ...options, ...selected.map((v) => ({ value: v, label: fallback(v) }))]

  const handleChange = (next: V, option?: Option | Option[]) => {
    const chosen = Array.isArray(option) ? option : option ? [option] : []
    if (chosen.length) setPicked((prev) => ({ ...prev, ...Object.fromEntries(chosen.map((o) => [o.value, o.label])) }))
    onChange?.(next)
  }

  return (
    <Select<V, Option>
      id={id}
      mode={multiple ? 'multiple' : undefined}
      value={value}
      onChange={handleChange}
      options={all}
      showSearch={{ filterOption: false, onSearch }}
      loading={loading}
      allowClear
      disabled={disabled}
      placeholder={placeholder}
      style={{ width: '100%', ...style }}
    />
  )
}

/** Picks active users of the workspace; `withMe` adds the dynamic "Me" value used in filters. */
export function UserSelect<V extends string | string[]>(props: PickerProps<V> & { withMe?: boolean }) {
  const [search, setSearch] = useState('')
  const q = useDebounced(search)
  const { data, isFetching } = useQuery({
    queryKey: ['user-lookup', q],
    queryFn: () => http.get<UserLookup[]>(`/users/lookup${qs({ q, limit: 50 })}`),
    staleTime: 60_000,
  })
  return (
    <RemoteSelect
      {...props}
      placeholder={props.placeholder ?? 'Select a user'}
      options={(data ?? []).map((u) => ({ value: u.id, label: u.fullName }))}
      extra={props.withMe ? [{ value: '$me', label: 'Me' }] : []}
      loading={isFetching}
      onSearch={setSearch}
    />
  )
}

/** Picks records of one module by searching their title, email or phone. */
export function RecordSelect<V extends string | string[]>({ module, ...props }: PickerProps<V> & { module?: string }) {
  const [search, setSearch] = useState('')
  const q = useDebounced(search)
  const { data, isFetching } = useQuery({
    queryKey: ['record-lookup', module, q],
    queryFn: () => http.get<PageResponse<RecordView>>(`/${module}${qs({ q, size: 10 })}`),
    enabled: !!module,
    staleTime: 30_000,
  })
  return (
    <RemoteSelect
      {...props}
      disabled={props.disabled || !module}
      placeholder={props.placeholder ?? 'Search…'}
      options={(data?.content ?? []).map((r) => ({ value: r.id, label: `${r.title} · ${r.recordNumber}` }))}
      loading={isFetching}
      onSearch={setSearch}
    />
  )
}
