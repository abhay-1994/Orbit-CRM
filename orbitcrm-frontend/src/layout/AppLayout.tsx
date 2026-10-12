import { CalendarOutlined, HomeFilled, PlusOutlined, SettingOutlined } from '@ant-design/icons'
import { Button, Dropdown, Skeleton, Tooltip } from 'antd'
import { Suspense, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { ChatButton } from '../chat/ChatButton'
import { ChatProvider } from '../chat/ChatProvider'
import { RecordFormDrawer } from '../components/RecordFormDrawer'
import { useCan } from '../lib/hooks'
import { MODULES } from '../lib/modules'
import { GlobalSearch } from './GlobalSearch'
import { Logo } from './Logo'
import { NotificationBell } from './NotificationBell'
import { UserMenu } from './UserMenu'

export function AppLayout() {
  const can = useCan()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const [creating, setCreating] = useState<string | null>(null)

  const readable = MODULES.filter((m) => can(m.key, 'READ'))
  const creatable = MODULES.filter((m) => can(m.key, 'CREATE'))
  const canAdminister =
    can('settings', 'UPDATE') || can('users', 'READ') || can('roles', 'READ') || can('teams', 'CREATE') || can('audit', 'READ')

  // "Deals" stays highlighted on deal pages but not on the pipeline board, which has its own tab.
  const navClass = (key: string) => ({ isActive }: { isActive: boolean }) =>
    isActive && !(key === 'deals' && pathname.startsWith('/deals/board')) ? 'active' : ''

  return (
    <ChatProvider>
      <header className="app-header">
        <Link to="/" className="logo">
          <Logo />
        </Link>
        <GlobalSearch modules={readable} />
        <div className="app-actions">
          {creatable.length > 0 && (
            <Dropdown
              trigger={['click']}
              menu={{
                items: creatable.map((m) => ({ key: m.key, icon: m.icon, label: `New ${m.singular.toLowerCase()}` })),
                onClick: ({ key }) => setCreating(key),
              }}
            >
              <Tooltip title="Quick create">
                <Button type="primary" shape="circle" icon={<PlusOutlined />} aria-label="Quick create" />
              </Tooltip>
            </Dropdown>
          )}
          {can('activities', 'READ') && (
            <Tooltip title="Calendar">
              <Button type="text" shape="circle" icon={<CalendarOutlined style={{ fontSize: 18 }} />} onClick={() => navigate('/calendar')} />
            </Tooltip>
          )}
          <ChatButton />
          <NotificationBell />
          {canAdminister && (
            <Tooltip title="Settings">
              <Button type="text" shape="circle" icon={<SettingOutlined style={{ fontSize: 18 }} />} onClick={() => navigate('/settings')} />
            </Tooltip>
          )}
          <UserMenu />
        </div>
      </header>

      <nav className="app-nav">
        <NavLink to="/" end aria-label="Dashboard">
          <HomeFilled style={{ fontSize: 17 }} />
        </NavLink>
        {readable.map((m) => (
          <NavLink key={m.key} to={`/${m.key}`} className={navClass(m.key)}>
            {m.label}
          </NavLink>
        ))}
        {can('deals', 'READ') && <NavLink to="/deals/board">Pipeline</NavLink>}
        {can('activities', 'READ') && <NavLink to="/calendar">Calendar</NavLink>}
      </nav>

      <main className="app-main">
        <Suspense fallback={<Skeleton active paragraph={{ rows: 10 }} />}>
          <Outlet />
        </Suspense>
      </main>

      {creating && (
        <RecordFormDrawer
          module={creating}
          onClose={() => setCreating(null)}
          onSaved={(record) => navigate(`/${record.module}/${record.id}`)}
        />
      )}
    </ChatProvider>
  )
}
