import { ArrowLeftOutlined, DeleteOutlined, EditOutlined, SwapOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Card, Col, Descriptions, Popconfirm, Result, Row, Skeleton, Table, Tabs } from 'antd'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { http } from '../../api/client'
import type { FieldMeta, RecordValues, RecordView, StageMove } from '../../api/types'
import { Attachments } from '../../components/Attachments'
import { FieldValue } from '../../components/FieldValue'
import { Notes } from '../../components/Notes'
import { RecordFormDrawer } from '../../components/RecordFormDrawer'
import { RecordTimeline } from '../../components/RecordTimeline'
import { RelatedList } from '../../components/RelatedList'
import { showError } from '../../lib/feedback'
import { formatDateTime, formatMoney, fromNow } from '../../lib/format'
import { useCan, useModuleMeta } from '../../lib/hooks'
import { MODULE_BY_KEY } from '../../lib/modules'
import { useMe } from '../../auth/context'
import { ConvertLeadModal } from './ConvertLeadModal'
import { StagePath } from './StagePath'

const GROUP_TITLES: Record<string, string> = { details: 'Details', address: 'Address', system: 'System', conversion: 'Conversion' }
const GROUP_ORDER = ['details', 'address', 'system', 'conversion']

interface Related {
  module: string
  title: string
  filter: unknown
  defaults: RecordValues
}

const eq = (field: string, value: string) => ({ field, op: 'eq', value })
const activitiesOf = (module: string, id: string): Related => ({
  module: 'activities',
  title: 'Activities',
  filter: { and: [eq('relatedModule', module), eq('relatedId', id)] },
  defaults: { relatedModule: module, relatedId: id },
})

/** Records of other modules shown as tabs on a record. */
const RELATED: Record<string, (id: string) => Related[]> = {
  companies: (id) => [
    { module: 'contacts', title: 'Contacts', filter: eq('companyId', id), defaults: { companyId: id } },
    { module: 'deals', title: 'Deals', filter: eq('companyId', id), defaults: { companyId: id } },
    activitiesOf('companies', id),
  ],
  contacts: (id) => [{ module: 'deals', title: 'Deals', filter: eq('contactId', id), defaults: { contactId: id } }, activitiesOf('contacts', id)],
  leads: (id) => [activitiesOf('leads', id)],
  deals: (id) => [activitiesOf('deals', id)],
  activities: () => [],
}

export function RecordDetailPage({ module }: { module: string }) {
  const { id = '' } = useParams()
  const can = useCan()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const meta = useModuleMeta(module)
  const info = MODULE_BY_KEY[module]
  const [editing, setEditing] = useState(false)
  const [converting, setConverting] = useState(false)

  const record = useQuery({
    queryKey: ['record', module, id],
    queryFn: () => http.get<RecordView>(`/${module}/${id}`),
  })

  const restore = async () => {
    try {
      await http.post(`/${module}/${id}/restore`)
      void queryClient.invalidateQueries({ queryKey: ['records', module] })
      navigate(`/${module}/${id}`)
    } catch (error) {
      showError(error)
    }
  }
  const remove = useMutation({
    mutationFn: () => http.delete(`/${module}/${id}`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['records', module] })
      queryClient.removeQueries({ queryKey: ['record', module, id] })
      message.success({
        content: (
          <span>
            {info.singular} deleted.{' '}
            <Button type="link" size="small" onClick={restore}>
              Undo
            </Button>
          </span>
        ),
        duration: 8,
      })
      navigate(`/${module}`)
    },
  })

  if (record.isError) {
    return (
      <Result
        status="404"
        title={`${info.singular} not found`}
        subTitle="It may have been deleted, or you don't have access to it."
        extra={<Link to={`/${module}`}>Back to {info.label.toLowerCase()}</Link>}
      />
    )
  }
  if (!record.data || !meta.data) return <Skeleton active paragraph={{ rows: 14 }} />

  const r = record.data
  const fields = meta.data.fields
  const converted = module === 'leads' && r.values.status === 'converted'
  const related = RELATED[module](r.id).filter((rel) => can(rel.module, 'READ'))
  const groups = GROUP_ORDER.map((group) => ({ group, fields: fields.filter((f) => f.group === group) })).filter(
    (g) => g.fields.length > 0 && !(g.group === 'conversion' && !converted),
  )

  return (
    <div>
      <Link to={`/${module}`} className="muted" style={{ display: 'inline-flex', gap: 6, marginBottom: 12 }}>
        <ArrowLeftOutlined /> All {info.label.toLowerCase()}
      </Link>

      <Card style={{ marginBottom: 16 }}>
        <div className="page-head" style={{ marginBottom: 0 }}>
          <div className="page-icon">{info.icon}</div>
          <div>
            <h1>{r.title}</h1>
            <p>
              {info.singular} · {r.recordNumber} · Owner: {r.labels.ownerId ?? '—'} · Updated {fromNow(r.values.updatedAt)}
            </p>
          </div>
          <div className="actions">
            {module === 'leads' && !converted && can('leads', 'UPDATE') && (
              <Button type="primary" icon={<SwapOutlined />} onClick={() => setConverting(true)}>
                Convert
              </Button>
            )}
            {can(module, 'UPDATE') && !converted && (
              <Button icon={<EditOutlined />} onClick={() => setEditing(true)}>
                Edit
              </Button>
            )}
            {can(module, 'DELETE') && (
              <Popconfirm title={`Delete this ${info.singular.toLowerCase()}?`} onConfirm={() => remove.mutate()}>
                <Button danger icon={<DeleteOutlined />} loading={remove.isPending}>
                  Delete
                </Button>
              </Popconfirm>
            )}
          </div>
        </div>
        {module === 'deals' && (
          <div style={{ marginTop: 20 }}>
            <StagePath deal={r} editable={can('deals', 'UPDATE')} />
          </div>
        )}
        {converted && (
          <Alert
            type="success"
            showIcon
            style={{ marginTop: 16 }}
            title={`Converted ${fromNow(r.values.convertedAt)}. This lead is now read-only.`}
            description={
              <span>
                {(['convertedContactId', 'convertedCompanyId', 'convertedDealId'] as const)
                  .filter((key) => r.values[key])
                  .map((key) => {
                    const target = key === 'convertedContactId' ? 'contacts' : key === 'convertedCompanyId' ? 'companies' : 'deals'
                    return (
                      <Link key={key} to={`/${target}/${r.values[key]}`} style={{ marginRight: 16 }}>
                        {MODULE_BY_KEY[target].singular}: {r.labels[key]}
                      </Link>
                    )
                  })}
              </span>
            }
          />
        )}
      </Card>

      <Row gutter={16}>
        <Col xs={24} xl={9}>
          <Card title="About this record" style={{ marginBottom: 16 }}>
            {groups.map(({ group, fields: groupFields }) => (
              <Descriptions
                key={group}
                title={GROUP_TITLES[group] ?? group}
                column={1}
                size="small"
                style={{ marginBottom: 16 }}
                items={groupFields.map((field: FieldMeta) => ({ key: field.key, label: field.label, children: <FieldValue field={field} record={r} /> }))}
              />
            ))}
          </Card>
        </Col>
        <Col xs={24} xl={15}>
          <Card>
            <Tabs
              items={[
                { key: 'timeline', label: 'Timeline', children: <RecordTimeline module={module} recordId={r.id} fields={fields} /> },
                { key: 'notes', label: 'Notes', children: <Notes module={module} recordId={r.id} /> },
                { key: 'files', label: 'Files', children: <Attachments module={module} recordId={r.id} /> },
                ...related.map((rel) => ({
                  key: rel.module,
                  label: rel.title,
                  children: (
                    <RelatedList
                      module={rel.module}
                      filter={rel.filter}
                      defaults={rel.defaults}
                      defaultLabels={Object.fromEntries(Object.keys(rel.defaults).map((key) => [key, r.title]))}
                    />
                  ),
                })),
                ...(module === 'deals' ? [{ key: 'stages', label: 'Stage history', children: <StageHistory dealId={r.id} /> }] : []),
              ]}
            />
          </Card>
        </Col>
      </Row>

      {editing && <RecordFormDrawer module={module} record={r} onClose={() => setEditing(false)} />}
      {converting && <ConvertLeadModal lead={r} onClose={() => setConverting(false)} />}
    </div>
  )
}

function duration(seconds?: number): string {
  if (seconds === undefined || seconds === null) return '—'
  if (seconds < 3600) return `${Math.max(1, Math.round(seconds / 60))} min`
  if (seconds < 86400) return `${Math.round(seconds / 3600)} h`
  return `${Math.round(seconds / 86400)} days`
}

function StageHistory({ dealId }: { dealId: string }) {
  const me = useMe()
  const history = useQuery({
    queryKey: ['stage-history', dealId],
    queryFn: () => http.get<StageMove[]>(`/deals/${dealId}/stage-history`),
  })
  return (
    <Table<StageMove>
      rowKey="changedAt"
      size="small"
      pagination={false}
      loading={history.isLoading}
      dataSource={history.data}
      columns={[
        { title: 'Moved', render: (_, m) => (m.fromStage ? `${m.fromStage} → ${m.toStage}` : `Started in ${m.toStage}`) },
        { title: 'Amount', render: (_, m) => (m.amount === undefined || m.amount === null ? '—' : formatMoney(m.amount, me.tenant)) },
        { title: 'When', render: (_, m) => formatDateTime(m.changedAt) },
        { title: 'Time in previous stage', render: (_, m) => duration(m.secondsInPriorStage) },
      ]}
    />
  )
}
