import { Tag } from 'antd'
import { Link } from 'react-router'
import type { FieldMeta, RecordView } from '../api/types'
import { useMe } from '../auth/context'
import { formatDate, formatDateTime, formatMoney } from '../lib/format'

function isEmpty(value: unknown): boolean {
  return value === null || value === undefined || value === '' || (Array.isArray(value) && value.length === 0)
}

/** Read-only display of one record field, using the server's labels for references and picklists. */
export function FieldValue({ field, record }: { field: FieldMeta; record: RecordView }) {
  const me = useMe()
  const value = record.values[field.key]
  const label = record.labels[field.key]
  if (isEmpty(value)) return <span className="muted">—</span>

  const option = (key: unknown) => field.options?.find((o) => o.key === key)

  switch (field.type) {
    case 'LOOKUP': {
      const target = field.lookupModule ?? (record.values[field.lookupModuleField ?? ''] as string | undefined)
      return target ? <Link to={`/${target}/${value}`}>{label ?? 'Open record'}</Link> : <>{label}</>
    }
    case 'PICKLIST':
      return <Tag color={option(value)?.color}>{label ?? option(value)?.label ?? String(value)}</Tag>
    case 'MULTI_PICKLIST':
    case 'TAGS':
      return (
        <>
          {(value as string[]).map((v) => (
            <Tag key={v} color={option(v)?.color}>
              {option(v)?.label ?? v}
            </Tag>
          ))}
        </>
      )
    case 'CURRENCY':
      return <>{formatMoney(value, me.tenant)}</>
    case 'PERCENT':
      return <>{Number(value)}%</>
    case 'DATE':
      return <>{formatDate(value)}</>
    case 'DATETIME':
      return <>{formatDateTime(value)}</>
    case 'BOOLEAN':
      return <>{value ? 'Yes' : 'No'}</>
    case 'EMAIL':
      return <a href={`mailto:${value}`}>{String(value)}</a>
    case 'PHONE':
      return <a href={`tel:${value}`}>{String(value)}</a>
    case 'URL':
      return (
        <a href={String(value)} target="_blank" rel="noreferrer">
          {String(value).replace(/^https?:\/\//, '')}
        </a>
      )
    default:
      return <>{label ?? String(value)}</>
  }
}
