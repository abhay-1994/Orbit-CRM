import { ClockCircleOutlined, BellOutlined, CalendarOutlined, CommentOutlined, UserSwitchOutlined } from '@ant-design/icons'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Badge, Button, Empty, Popover, Spin, Typography } from 'antd'
import { useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { http } from '../api/client'
import type { NotificationView, PageResponse } from '../api/types'
import { fromNow } from '../lib/format'

const ICONS: Record<string, ReactNode> = {
  RECORD_ASSIGNED: <UserSwitchOutlined />,
  MENTION: <CommentOutlined />,
  ACTIVITY_INVITE: <CalendarOutlined />,
  ACTIVITY_REMINDER: <ClockCircleOutlined />,
}

/** Unread badge (polled every 30 s) and a panel with the latest notifications. */
export function NotificationBell() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)

  const unread = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => http.get<{ unread: number }>('/notifications/unread-count'),
    refetchInterval: 30_000,
  })
  const latest = useQuery({
    queryKey: ['notifications', 'latest'],
    queryFn: () => http.get<PageResponse<NotificationView>>('/notifications?size=8'),
    enabled: open,
  })

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['notifications'] })

  const openItem = async (item: NotificationView) => {
    if (!item.readAt) await http.post(`/notifications/${item.id}/read`)
    void refresh()
    setOpen(false)
    if (item.moduleKey && item.recordId) navigate(`/${item.moduleKey}/${item.recordId}`)
  }

  const markAllRead = async () => {
    await http.post('/notifications/read-all')
    void refresh()
  }

  const items = latest.data?.content ?? []
  const panel = (
    <div className="notif-panel">
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: 8 }}>
        <Typography.Text strong>Notifications</Typography.Text>
        <Button type="link" size="small" style={{ marginLeft: 'auto' }} onClick={markAllRead}>
          Mark all as read
        </Button>
      </div>
      {latest.isLoading ? (
        <Spin style={{ display: 'block', margin: 24 }} />
      ) : items.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="You're all caught up" />
      ) : (
        items.map((item) => (
          <div key={item.id} className={`notif-item${item.readAt ? '' : ' unread'}`} onClick={() => openItem(item)}>
            <span className="tone-purple" style={{ borderRadius: 8, padding: '4px 8px', height: 'fit-content' }}>
              {ICONS[item.type] ?? <BellOutlined />}
            </span>
            <div style={{ minWidth: 0 }}>
              <div style={{ fontWeight: item.readAt ? 400 : 600 }}>{item.title}</div>
              {item.body && (
                <Typography.Text type="secondary" ellipsis style={{ display: 'block' }}>
                  {item.body}
                </Typography.Text>
              )}
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {fromNow(item.createdAt)}
              </Typography.Text>
            </div>
          </div>
        ))
      )}
    </div>
  )

  return (
    <Popover open={open} onOpenChange={setOpen} trigger="click" placement="bottomRight" content={panel} arrow={false}>
      <Badge count={unread.data?.unread} size="small" offset={[-4, 4]}>
        <Button type="text" shape="circle" icon={<BellOutlined style={{ fontSize: 18 }} />} aria-label="Notifications" />
      </Badge>
    </Popover>
  )
}
