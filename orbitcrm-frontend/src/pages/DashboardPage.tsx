import {
  AimOutlined,
  AppstoreOutlined,
  CalendarOutlined,
  CheckSquareOutlined,
  DollarOutlined,
  FieldTimeOutlined,
  FundOutlined,
  HomeOutlined,
  RocketOutlined,
  RiseOutlined,
  TrophyOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Avatar, Button, Card, Col, Empty, Progress, Row, Select, Skeleton, Space, Table, Tag, Typography } from 'antd'
import dayjs from 'dayjs'
import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { http, qs } from '../api/client'
import type { Dashboard, PageResponse, RecordView, StageType } from '../api/types'
import { useMe } from '../auth/context'
import { BarChart } from '../components/BarChart'
import { ACTIVITY_STYLE, OPEN_ACTIVITY_STATUSES } from '../lib/activity'
import { formatDate, formatMoney, formatTime, fromNow, initials, plural } from '../lib/format'
import { useCan, useModuleMeta } from '../lib/hooks'
import { brand } from '../theme'

const RANGES = [
  { value: 'today', label: 'Today', phrase: 'today' },
  { value: 'this_week', label: 'This week', phrase: 'this week' },
  { value: 'this_month', label: 'This month', phrase: 'this month' },
  { value: 'last_30_days', label: 'Last 30 days', phrase: 'in the last 30 days' },
  { value: 'this_quarter', label: 'This quarter', phrase: 'this quarter' },
  { value: 'this_year', label: 'This year', phrase: 'this year' },
]

export function DashboardPage() {
  const me = useMe()
  const can = useCan()
  const [range, setRange] = useState('this_month')
  const phrase = RANGES.find((r) => r.value === range)?.phrase ?? ''
  const { data, isLoading } = useQuery({
    queryKey: ['dashboard', range],
    queryFn: () => http.get<Dashboard>(`/dashboard${qs({ range })}`),
  })
  const money = (v: number, compact = true) => formatMoney(v, me.tenant, compact)

  return (
    <div className="with-side">
      <Favorites />
      <div>
        <div className="page-head">
          <div className="page-icon">
            <FundOutlined />
          </div>
          <div>
            <h1>Dashboard</h1>
            <p>
              Welcome, {me.firstName}! Here's what's happening in {me.tenant.name}.
            </p>
          </div>
          <div className="actions">
            <Select value={range} onChange={setRange} options={RANGES} style={{ width: 160 }} />
          </div>
        </div>

        {isLoading || !data ? (
          <Skeleton active paragraph={{ rows: 12 }} />
        ) : (
          <Row gutter={[16, 16]}>
            {data.leads?.open === 0 && !data.deals?.openCount && !data.deals?.wonCount && <GetStarted />}
            {data.leads && (
              <Kpi icon={<AimOutlined />} tone="navy" label="Open leads" value={data.leads.open}
                note={`${data.leads.created} new ${phrase} · ${data.leads.conversionRatePercent}% converted`} />
            )}
            {data.deals && (
              <>
                <Kpi icon={<DollarOutlined />} tone="purple" label="Open deals" value={data.deals.openCount}
                  note={`${money(data.deals.pipelineValue)} pipeline · ${money(data.deals.weightedPipeline)} weighted`} />
                <Kpi icon={<TrophyOutlined />} tone="cyan" label={`Won ${phrase}`} value={money(data.deals.wonRevenue)}
                  note={`${plural(data.deals.wonCount, 'deal')} won`} />
                <Kpi icon={<RiseOutlined />} tone="mix" label="Win rate" value={`${data.deals.winRatePercent}%`}
                  note={`${data.deals.wonCount} won · ${data.deals.lostCount} lost ${phrase}`} />
              </>
            )}

            {data.funnel && (
              <Col span={24}>
                <Card title="Sales pipeline" extra={<Link to="/deals/board">Open board</Link>}>
                  {data.funnel.length === 0 ? (
                    <Empty description="No pipeline configured" />
                  ) : (
                    <div className="funnel">
                      {data.funnel.map((stage, i) => (
                        <div key={stage.stageId} className="chevron" style={stageStyle(stage.type, i, data.funnel!.length)} title={stage.stage}>
                          <div className="name">{stage.stage}</div>
                          <div className="count">{stage.count}</div>
                          <div className="amount">{money(stage.amount)}</div>
                        </div>
                      ))}
                    </div>
                  )}
                </Card>
              </Col>
            )}

            {data.revenueByMonth && (
              <Col xs={24} xl={14}>
                <Card
                  title="Revenue overview"
                  extra={<Typography.Text type="secondary">Won in the last 12 months: {money(data.revenueByMonth.reduce((s, m) => s + m.revenue, 0))}</Typography.Text>}
                >
                  {data.revenueByMonth.every((m) => m.revenue === 0) ? (
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No deals won in the last 12 months yet" />
                  ) : (
                    <BarChart
                      formatAxis={(v) => money(v)}
                      data={data.revenueByMonth.map((m) => {
                        const label = dayjs(`${m.month}-01`).format('MMM')
                        return { label, value: m.revenue, tooltip: `${label}: ${money(m.revenue, false)} from ${plural(m.wonCount, 'won deal')}` }
                      })}
                    />
                  )}
                </Card>
              </Col>
            )}

            {data.leadSources && (
              <Col xs={24} xl={10}>
                <Card title={`Leads by source ${phrase}`} style={{ height: '100%' }}>
                  {data.leadSources.length === 0 ? (
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No new leads yet" />
                  ) : (
                    data.leadSources.slice(0, 7).map((source) => (
                      <div key={source.label} style={{ marginBottom: 10 }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                          <span>{source.label}</span>
                          <strong>{source.count}</strong>
                        </div>
                        <Progress
                          percent={(source.count / data.leadSources![0].count) * 100}
                          showInfo={false}
                          strokeColor={{ from: brand.purple, to: brand.cyan }}
                          size="small"
                        />
                      </div>
                    ))
                  )}
                </Card>
              </Col>
            )}

            {can('leads', 'READ') && (
              <Col xs={24} xl={10}>
                <RecentLeads />
              </Col>
            )}
            {can('activities', 'READ') && (
              <>
                <Col xs={24} md={12} xl={7}>
                  <UpcomingActivities dueToday={data.activities?.dueToday ?? 0} overdue={data.activities?.overdue ?? 0} />
                </Col>
                <Col xs={24} md={12} xl={7}>
                  <Agenda />
                </Col>
              </>
            )}
          </Row>
        )}
      </div>
    </div>
  )
}

/** Shown while the workspace has no leads or deals yet. */
function GetStarted() {
  const can = useCan()
  return (
    <Col span={24}>
      <Card className="tone-mix" style={{ borderColor: 'transparent' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
          <RocketOutlined style={{ fontSize: 32 }} />
          <div style={{ flex: 1, minWidth: 220 }}>
            <Typography.Title level={4} style={{ margin: 0 }}>
              Let's get your workspace going
            </Typography.Title>
            <span className="muted">Add your first lead, bring in an existing list, or invite your team.</span>
          </div>
          <Space wrap>
            {can('leads', 'CREATE') && (
              <Link to="/leads">
                <Button type="primary">Add leads</Button>
              </Link>
            )}
            {can('leads', 'IMPORT') && (
              <Link to="/import?module=leads">
                <Button>Import a spreadsheet</Button>
              </Link>
            )}
            {can('users', 'CREATE') && (
              <Link to="/settings/users">
                <Button>Invite your team</Button>
              </Link>
            )}
          </Space>
        </div>
      </Card>
    </Col>
  )
}

function Kpi({ icon, tone, label, value, note }: { icon: ReactNode; tone: string; label: string; value: ReactNode; note: string }) {
  return (
    <Col xs={24} sm={12} xl={6}>
      <Card style={{ height: '100%' }}>
        <div className="kpi">
          <div className={`kpi-icon tone-${tone}`}>{icon}</div>
          <div style={{ minWidth: 0 }}>
            <div className="muted">{label}</div>
            <div className="kpi-value">{value}</div>
            <div className="kpi-note">{note}</div>
          </div>
        </div>
      </Card>
    </Col>
  )
}

/** Open stages move from navy to purple to cyan; won is cyan, lost is red. */
function stageStyle(type: StageType, index: number, count: number) {
  if (type === 'LOST') return { background: '#fde8e8', color: '#b42318' }
  if (type === 'WON') return { background: 'rgba(30, 220, 252, 0.28)', color: brand.navy }
  const t = count > 1 ? index / (count - 1) : 0
  const tints = ['rgba(3, 37, 119, 0.10)', 'rgba(91, 41, 199, 0.13)', 'rgba(189, 56, 250, 0.15)', 'rgba(30, 220, 252, 0.18)']
  return { background: tints[Math.min(tints.length - 1, Math.floor(t * tints.length))], color: brand.navy }
}

function Favorites() {
  const can = useCan()
  const links = [
    can('leads', 'READ') && { to: '/leads?view=My Open Leads', icon: <AimOutlined />, label: 'My open leads' },
    can('deals', 'READ') && { to: '/deals?view=My Open Deals', icon: <DollarOutlined />, label: 'My open deals' },
    can('deals', 'READ') && { to: '/deals?view=Closing This Month', icon: <FieldTimeOutlined />, label: 'Closing this month' },
    can('activities', 'READ') && { to: '/activities?view=My Open Tasks', icon: <CheckSquareOutlined />, label: 'My open tasks' },
    can('activities', 'READ') && { to: '/activities?view=Overdue', icon: <WarningOutlined />, label: 'Overdue' },
    can('activities', 'READ') && { to: '/calendar', icon: <CalendarOutlined />, label: 'My calendar' },
    can('deals', 'READ') && { to: '/deals/board', icon: <AppstoreOutlined />, label: 'Pipeline board' },
  ].filter((link) => !!link)

  return (
    <aside className="side-panel">
      <Link to="/" className="side-link active">
        <HomeOutlined />
        Dashboard
      </Link>
      <h4>Favorites</h4>
      {links.map((link) => (
        <Link key={link.to} to={link.to} className="side-link">
          {link.icon}
          {link.label}
        </Link>
      ))}
    </aside>
  )
}

function RecentLeads() {
  const meta = useModuleMeta('leads')
  const { data, isLoading } = useQuery({
    queryKey: ['records', 'leads', 'recent'],
    queryFn: () => http.post<PageResponse<RecordView>>('/leads/search', { sort: [{ field: 'createdAt', direction: 'desc' }], size: 6 }),
  })
  const statusColor = (key: unknown) => meta.data?.fields.find((f) => f.key === 'status')?.options?.find((o) => o.key === key)?.color

  return (
    <Card title="Recent leads" extra={<Link to="/leads">View all</Link>} styles={{ body: { padding: '0 12px 8px' } }} style={{ height: '100%' }}>
      <Table<RecordView>
        rowKey="id"
        size="small"
        loading={isLoading}
        pagination={false}
        dataSource={data?.content}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No leads yet" /> }}
        columns={[
          {
            title: 'Name',
            render: (_, lead) => (
              <Link to={`/leads/${lead.id}`} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Avatar size="small" className="avatar-brand">
                  {initials(lead.title)}
                </Avatar>
                {lead.title}
              </Link>
            ),
          },
          { title: 'Company', render: (_, lead) => (lead.values.companyName as string) ?? '—' },
          { title: 'Source', render: (_, lead) => lead.labels.leadSource ?? '—' },
          { title: 'Status', render: (_, lead) => <Tag color={statusColor(lead.values.status)}>{lead.labels.status}</Tag> },
          { title: 'Added', render: (_, lead) => <span className="muted">{fromNow(lead.values.createdAt)}</span> },
        ]}
      />
    </Card>
  )
}

function UpcomingActivities({ dueToday, overdue }: { dueToday: number; overdue: number }) {
  const { data, isLoading } = useQuery({
    queryKey: ['records', 'activities', 'upcoming'],
    queryFn: () =>
      http.post<PageResponse<RecordView>>('/activities/search', {
        filter: {
          and: [
            { field: 'status', op: 'in', value: OPEN_ACTIVITY_STATUSES },
            { field: 'ownerId', op: 'eq', value: '$me' },
          ],
        },
        sort: [{ field: 'startAt', direction: 'asc' }],
        size: 6,
      }),
  })
  const now = dayjs()

  return (
    <Card
      title="My open activities"
      extra={<Link to="/activities?view=My Open Tasks">View all</Link>}
      style={{ height: '100%' }}
    >
      <div className="muted" style={{ marginBottom: 8 }}>
        {dueToday} due today · <span style={{ color: overdue ? '#cf1322' : undefined }}>{overdue} overdue</span>
      </div>
      {isLoading ? (
        <Skeleton active />
      ) : !data?.content.length ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Nothing planned" />
      ) : (
        data.content.map((activity) => {
          const style = ACTIVITY_STYLE[activity.values.type as string] ?? ACTIVITY_STYLE.task
          const when = (activity.values.startAt ?? activity.values.endAt) as string | undefined
          const late = !!activity.values.endAt && dayjs(activity.values.endAt as string).isBefore(now)
          return (
            <Link key={activity.id} to={`/activities/${activity.id}`} className="agenda-item" style={{ color: 'inherit' }}>
              <span style={{ color: style.color, fontSize: 18 }}>{style.icon}</span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <Typography.Text strong ellipsis style={{ display: 'block' }}>
                  {activity.title}
                </Typography.Text>
                <span className="muted">{activity.labels.relatedId ?? activity.labels.type}</span>
              </div>
              <div style={{ textAlign: 'right', fontSize: 13 }}>
                {late ? <Tag color="red">Overdue</Tag> : <span className="muted">{when ? (dayjs(when).isSame(now, 'day') ? formatTime(when) : formatDate(when)) : '—'}</span>}
              </div>
            </Link>
          )
        })
      )}
    </Card>
  )
}

function Agenda() {
  const today = dayjs().startOf('day')
  const { data, isLoading } = useQuery({
    queryKey: ['calendar', 'agenda', today.format('YYYY-MM-DD')],
    queryFn: () => http.get<RecordView[]>(`/calendar${qs({ from: today.toISOString(), to: today.add(2, 'day').toISOString() })}`),
  })
  const days = [today, today.add(1, 'day')].map((day, i) => ({
    title: `${i === 0 ? 'Today' : 'Tomorrow'}, ${day.format('D MMM')}`,
    items: (data ?? []).filter((a) => dayjs(a.values.startAt as string).isSame(day, 'day')),
  }))

  return (
    <Card title="My calendar" extra={<Link to="/calendar">Open calendar</Link>} style={{ height: '100%' }}>
      {isLoading ? (
        <Skeleton active />
      ) : (
        days.map((day) => (
          <div key={day.title} style={{ marginBottom: 12 }}>
            <Typography.Text strong>{day.title}</Typography.Text>
            {day.items.length === 0 ? (
              <div className="muted" style={{ padding: '6px 0' }}>No activities</div>
            ) : (
              day.items.map((a) => (
                <Link key={a.id} to={`/activities/${a.id}`} className="agenda-item" style={{ color: 'inherit' }}>
                  <span className="agenda-time">{a.values.allDay ? 'All day' : formatTime(a.values.startAt)}</span>
                  <span className="agenda-bar" style={{ background: (ACTIVITY_STYLE[a.values.type as string] ?? ACTIVITY_STYLE.task).color }} />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <Typography.Text strong ellipsis style={{ display: 'block' }}>
                      {a.title}
                    </Typography.Text>
                    <span className="muted">{(a.values.location as string) ?? a.labels.type}</span>
                  </div>
                </Link>
              ))
            )}
          </div>
        ))
      )}
    </Card>
  )
}
