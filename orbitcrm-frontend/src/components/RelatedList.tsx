import { PlusOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Button, Empty, Skeleton } from 'antd'
import { useState } from 'react'
import { Link } from 'react-router'
import { http } from '../api/client'
import type { PageResponse, RecordValues, RecordView } from '../api/types'
import { useCan, useModuleMeta } from '../lib/hooks'
import { MODULE_BY_KEY } from '../lib/modules'
import { FieldValue } from './FieldValue'
import { RecordFormDrawer } from './RecordFormDrawer'

interface Props {
  module: string
  filter: unknown
  /** Values for "New …", linking the new record to the one being viewed. */
  defaults: RecordValues
  defaultLabels: Record<string, string>
}

/** Records of another module that point at this record, e.g. a company's contacts. */
export function RelatedList({ module, filter, defaults, defaultLabels }: Props) {
  const can = useCan()
  const meta = useModuleMeta(module)
  const [creating, setCreating] = useState(false)
  const records = useQuery({
    queryKey: ['records', module, 'related', filter],
    queryFn: () => http.post<PageResponse<RecordView>>(`/${module}/search`, { filter, size: 20, sort: [{ field: 'createdAt', direction: 'desc' }] }),
  })
  const info = MODULE_BY_KEY[module]
  const shown = (meta.data?.fields ?? [])
    .filter((f) => f.listDefault && !meta.data?.titleFields.includes(f.key) && f.key !== 'ownerId')
    .slice(0, 3)

  return (
    <div>
      {can(module, 'CREATE') && (
        <Button icon={<PlusOutlined />} onClick={() => setCreating(true)} style={{ marginBottom: 12 }}>
          New {info.singular.toLowerCase()}
        </Button>
      )}
      {records.isLoading ? (
        <Skeleton active />
      ) : !records.data?.content.length ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={`No ${info.label.toLowerCase()} yet`} />
      ) : (
        records.data.content.map((record) => (
          <div key={record.id} style={{ padding: '10px 0', borderTop: '1px solid #f0f1f6' }}>
            <Link to={`/${module}/${record.id}`}>
              <strong>{record.title}</strong>
            </Link>{' '}
            <span className="muted" style={{ fontSize: 12 }}>
              {record.recordNumber}
            </span>
            <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginTop: 2 }}>
              {shown.map((field) => (
                <span key={field.key} style={{ fontSize: 13 }}>
                  <span className="muted">{field.label}: </span>
                  <FieldValue field={field} record={record} />
                </span>
              ))}
            </div>
          </div>
        ))
      )}
      {creating && <RecordFormDrawer module={module} initialValues={defaults} initialLabels={defaultLabels} onClose={() => setCreating(false)} />}
    </div>
  )
}
