import { CommentOutlined, EditOutlined, PaperClipOutlined, PlusCircleOutlined, SwapOutlined } from '@ant-design/icons'
import { useInfiniteQuery } from '@tanstack/react-query'
import { Button, Empty, Skeleton, Tag, Timeline, Typography } from 'antd'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { http, qs } from '../api/client'
import type { AuditChange, FieldMeta, Timeline as TimelinePage, TimelineItem } from '../api/types'
import { useMe } from '../auth/context'
import { ACTIVITY_STYLE } from '../lib/activity'
import { formatDate, formatDateTime, formatMoney, fromNow } from '../lib/format'
import { brand } from '../theme'

/** Everything that happened to a record, newest first: changes, notes, files and related activities. */
export function RecordTimeline({ module, recordId, fields }: { module: string; recordId: string; fields: FieldMeta[] }) {
  const timeline = useInfiniteQuery({
    queryKey: ['timeline', module, recordId],
    queryFn: ({ pageParam }) => http.get<TimelinePage>(`/${module}/${recordId}/timeline${qs({ before: pageParam, limit: 30 })}`),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextBefore ?? undefined,
  })

  if (timeline.isLoading) return <Skeleton active />
  const items = timeline.data?.pages.flatMap((page) => page.items) ?? []
  if (items.length === 0) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Nothing has happened yet" />

  return (
    <>
      <Timeline items={items.map((item) => ({ ...look(item), content: <Entry item={item} fields={fields} /> }))} />
      {timeline.hasNextPage && (
        <Button block onClick={() => timeline.fetchNextPage()} loading={timeline.isFetchingNextPage}>
          Show older
        </Button>
      )}
    </>
  )
}

function look(item: TimelineItem): { color: string; icon: ReactNode } {
  switch (item.kind) {
    case 'note':
      return { color: brand.purple, icon: <CommentOutlined /> }
    case 'attachment':
      return { color: brand.cyan, icon: <PaperClipOutlined /> }
    case 'activity': {
      const style = ACTIVITY_STYLE[item.action] ?? ACTIVITY_STYLE.task
      return { color: style.color, icon: style.icon }
    }
    default:
      return {
        color: brand.navy,
        icon: item.action === 'create' ? <PlusCircleOutlined /> : item.action === 'convert' ? <SwapOutlined /> : <EditOutlined />,
      }
  }
}

function Entry({ item, fields }: { item: TimelineItem; fields: FieldMeta[] }) {
  const byline = (
    <span className="muted" style={{ fontSize: 12 }}>
      {item.actorName ? `${item.actorName} · ` : ''}
      {fromNow(item.at)}
    </span>
  )
  switch (item.kind) {
    case 'note':
      return (
        <div>
          <strong>Note</strong> {byline}
          <Typography.Paragraph ellipsis={{ rows: 3, expandable: true }} style={{ whiteSpace: 'pre-wrap', margin: '2px 0 0' }}>
            {item.summary}
          </Typography.Paragraph>
        </div>
      )
    case 'attachment':
      return (
        <div>
          <strong>Uploaded {item.summary}</strong> {byline}
        </div>
      )
    case 'activity':
      return (
        <div>
          <Link to={`/activities/${item.refId}`}>
            <strong>{item.summary}</strong>
          </Link>{' '}
          {byline}
          <div>
            <Tag>{String(item.detail?.status ?? '').replace('_', ' ')}</Tag>
            {item.detail?.startAt ? <span className="muted">{formatDateTime(item.detail.startAt)}</span> : null}
          </div>
        </div>
      )
    default:
      return (
        <div>
          <strong>{item.summary}</strong> {byline}
          {item.action === 'update' && <Changes changes={item.detail?.changes ?? []} fields={fields} />}
        </div>
      )
  }
}

function Changes({ changes, fields }: { changes: AuditChange[]; fields: FieldMeta[] }) {
  const me = useMe()
  const show = (field: FieldMeta | undefined, value: unknown): string | null => {
    if (value === null || value === undefined || value === '') return 'empty'
    const option = (key: unknown) => field?.options?.find((o) => o.key === key)?.label ?? String(key)
    switch (field?.type) {
      case 'USER':
      case 'MULTI_USER':
      case 'LOOKUP':
        return null // ids are not meaningful to people; the change itself is shown
      case 'PICKLIST':
      case 'REFERENCE':
        return option(value)
      case 'MULTI_PICKLIST':
      case 'TAGS':
        return (value as unknown[]).map(option).join(', ')
      case 'DATE':
        return formatDate(value)
      case 'DATETIME':
        return formatDateTime(value)
      case 'CURRENCY':
        return formatMoney(value, me.tenant)
      case 'BOOLEAN':
        return value ? 'Yes' : 'No'
      default:
        return String(value)
    }
  }
  return (
    <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>
      {changes.map((change) => {
        const field = fields.find((f) => f.key === change.field)
        const from = show(field, change.oldValue)
        const to = show(field, change.newValue)
        return (
          <li key={change.field} className="muted">
            <span style={{ color: '#1f2a4d' }}>{change.label}</span>
            {to === null ? ' changed' : `: ${from} → ${to}`}
          </li>
        )
      })}
    </ul>
  )
}
