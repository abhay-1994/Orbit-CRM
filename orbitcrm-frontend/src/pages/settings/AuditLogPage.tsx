import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { DatePicker, Select, Space, Table, Tag } from 'antd'
import type { Dayjs } from 'dayjs'
import { useState } from 'react'
import { Link } from 'react-router'
import { http, qs } from '../../api/client'
import type { AuditLogView, PageResponse } from '../../api/types'
import { formatDateTime } from '../../lib/format'
import { useCan } from '../../lib/hooks'
import { MODULES } from '../../lib/modules'
import { SettingsSection } from './SettingsLayout'

const ACTIONS = [
  'CREATE', 'UPDATE', 'DELETE', 'RESTORE', 'CONVERT', 'IMPORT', 'EXPORT',
  'LOGIN', 'LOGIN_FAILED', 'LOGOUT', 'PASSWORD_CHANGED', 'PASSWORD_RESET', 'MFA_ENABLED', 'MFA_DISABLED',
  'USER_INVITED', 'USER_UPDATED', 'ROLE_CREATED', 'ROLE_UPDATED', 'ROLE_DELETED', 'SETTINGS_UPDATED',
]
const SECURITY = new Set(['LOGIN_FAILED', 'PASSWORD_RESET', 'MFA_DISABLED', 'DELETE', 'ROLE_DELETED'])

const label = (action: string) => action.charAt(0) + action.slice(1).toLowerCase().replaceAll('_', ' ')
const show = (value: unknown) => (value === null || value === undefined || value === '' ? 'empty' : typeof value === 'object' ? JSON.stringify(value) : String(value))

/** Who did what and when: record changes, sign-ins, exports and admin changes. Entries can't be edited. */
export function AuditLogPage() {
  const can = useCan()
  const [module, setModule] = useState<string>()
  const [action, setAction] = useState<string>()
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)
  const [paging, setPaging] = useState({ page: 0, size: 25 })

  const params = {
    module,
    action,
    from: range?.[0]?.startOf('day').toISOString(),
    to: range?.[1]?.endOf('day').toISOString(),
    page: paging.page,
    size: paging.size,
  }
  const logs = useQuery({
    queryKey: ['audit', params],
    queryFn: () => http.get<PageResponse<AuditLogView>>(`/audit-logs${qs(params)}`),
    enabled: can('audit', 'READ'),
    placeholderData: keepPreviousData,
  })

  return (
    <SettingsSection title="Audit log" description="A permanent record of changes and security events. Nobody can edit or delete entries." allowed={can('audit', 'READ')}>
      <Space wrap style={{ marginBottom: 16 }}>
        <Select allowClear placeholder="All areas" style={{ width: 160 }} value={module} onChange={setModule} options={MODULES.map((m) => ({ value: m.key, label: m.label }))} />
        <Select allowClear showSearch placeholder="All actions" style={{ width: 200 }} value={action} onChange={setAction} options={ACTIONS.map((a) => ({ value: a, label: label(a) }))} />
        <DatePicker.RangePicker value={range} onChange={(dates) => setRange(dates)} />
      </Space>
      <Table<AuditLogView>
        rowKey="id"
        size="small"
        loading={logs.isFetching}
        dataSource={logs.data?.content}
        pagination={{
          current: paging.page + 1,
          pageSize: paging.size,
          total: logs.data?.totalElements,
          onChange: (page, size) => setPaging({ page: page - 1, size }),
        }}
        expandable={{
          rowExpandable: (entry) => !!entry.changes?.length || !!entry.metadata,
          expandedRowRender: (entry) => (
            <div style={{ paddingLeft: 24 }}>
              {entry.changes?.map((change) => (
                <div key={change.field}>
                  <strong>{change.label}</strong>: {show(change.oldValue)} → {show(change.newValue)}
                </div>
              ))}
              {entry.metadata && <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{JSON.stringify(entry.metadata, null, 2)}</pre>}
            </div>
          ),
        }}
        columns={[
          { title: 'When', width: 190, render: (_, e) => formatDateTime(e.occurredAt) },
          { title: 'Who', render: (_, e) => e.actorName ?? <span className="muted">System</span> },
          { title: 'Action', render: (_, e) => <Tag color={SECURITY.has(e.action) ? 'red' : 'purple'}>{label(e.action)}</Tag> },
          {
            title: 'Record',
            render: (_, e) =>
              e.recordId && e.moduleKey ? (
                <Link to={`/${e.moduleKey}/${e.recordId}`}>{e.recordLabel ?? 'Open'}</Link>
              ) : (
                <span className="muted">{e.moduleKey ?? '—'}</span>
              ),
          },
          { title: 'IP address', render: (_, e) => <span className="muted">{e.ipAddress}</span> },
        ]}
      />
    </SettingsSection>
  )
}
