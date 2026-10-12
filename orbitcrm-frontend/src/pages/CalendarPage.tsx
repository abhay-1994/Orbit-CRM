import { CalendarOutlined, PlusOutlined } from '@ant-design/icons'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Button, Calendar, Card, Space } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useState } from 'react'
import { Link } from 'react-router'
import { http, qs } from '../api/client'
import type { RecordValues, RecordView } from '../api/types'
import { UserSelect } from '../components/pickers'
import { RecordFormDrawer } from '../components/RecordFormDrawer'
import { ACTIVITY_STYLE } from '../lib/activity'
import { formatTime } from '../lib/format'
import { useCan } from '../lib/hooks'

/** Month view of activities for you (or chosen colleagues). Click a day to schedule something. */
export function CalendarPage() {
  const can = useCan()
  const [month, setMonth] = useState<Dayjs>(() => dayjs())
  const [users, setUsers] = useState<string[]>([])
  const [creating, setCreating] = useState<RecordValues | null>(null)

  const from = month.startOf('month').startOf('week')
  const to = month.endOf('month').endOf('week').add(1, 'millisecond')
  const events = useQuery({
    queryKey: ['calendar', from.toISOString(), users],
    queryFn: () => http.get<RecordView[]>(`/calendar${qs({ from: from.toISOString(), to: to.toISOString(), userId: users })}`),
    placeholderData: keepPreviousData,
  })

  const byDay = new Map<string, RecordView[]>()
  for (const event of events.data ?? []) {
    const day = dayjs(event.values.startAt as string).format('YYYY-MM-DD')
    byDay.set(day, [...(byDay.get(day) ?? []), event])
  }

  const schedule = (day: Dayjs) =>
    setCreating({
      type: 'meeting',
      startAt: day.hour(10).minute(0).second(0).toISOString(),
      endAt: day.hour(11).minute(0).second(0).toISOString(),
    })

  return (
    <div>
      <div className="page-head">
        <div className="page-icon">
          <CalendarOutlined />
        </div>
        <div>
          <h1>Calendar</h1>
          <p>Activities you own or are invited to{users.length ? ', plus the selected colleagues' : ''}.</p>
        </div>
        <div className="actions">
          <UserSelect<string[]> multiple value={users} onChange={setUsers} placeholder="Show colleagues' calendars" style={{ width: 280 }} />
          {can('activities', 'CREATE') && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => schedule(dayjs())}>
              New activity
            </Button>
          )}
        </div>
      </div>
      <Card>
        <Space wrap style={{ marginBottom: 8 }}>
          {Object.entries(ACTIVITY_STYLE).map(([type, style]) => (
            <span key={type} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, marginRight: 12 }}>
              <span style={{ width: 10, height: 10, borderRadius: 3, background: style.color }} />
              <span style={{ textTransform: 'capitalize' }}>{type}</span>
            </span>
          ))}
        </Space>
        <Calendar
          value={month}
          onPanelChange={(date) => setMonth(date)}
          onSelect={(date, info) => {
            setMonth(date)
            if (info.source === 'date' && can('activities', 'CREATE')) schedule(date)
          }}
          cellRender={(date, info) => {
            if (info.type !== 'date') return info.originNode
            const items = byDay.get(date.format('YYYY-MM-DD')) ?? []
            return (
              <>
                {items.slice(0, 3).map((event) => (
                  <Link
                    key={event.id}
                    to={`/activities/${event.id}`}
                    className="cal-event"
                    style={{ background: (ACTIVITY_STYLE[event.values.type as string] ?? ACTIVITY_STYLE.task).color }}
                    onClick={(e) => e.stopPropagation()}
                    title={event.title}
                  >
                    {event.values.allDay ? '' : `${formatTime(event.values.startAt)} `}
                    {event.title}
                  </Link>
                ))}
                {items.length > 3 && <span className="muted" style={{ fontSize: 12 }}>+{items.length - 3} more</span>}
              </>
            )
          }}
        />
      </Card>
      {creating && <RecordFormDrawer module="activities" initialValues={creating} onClose={() => setCreating(null)} />}
    </div>
  )
}
