import {
  ApartmentOutlined,
  AuditOutlined,
  GlobalOutlined,
  NodeIndexOutlined,
  SafetyCertificateOutlined,
  TagsOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Card, Result } from 'antd'
import type { ReactNode } from 'react'
import { NavLink, Outlet } from 'react-router'
import { useCan } from '../../lib/hooks'

export function SettingsLayout() {
  const can = useCan()
  const sections = [
    { to: 'workspace', label: 'Workspace', icon: <GlobalOutlined />, allowed: true },
    { to: 'users', label: 'Users', icon: <UserOutlined />, allowed: can('users', 'READ') },
    { to: 'teams', label: 'Teams', icon: <ApartmentOutlined />, allowed: true },
    { to: 'roles', label: 'Roles & permissions', icon: <SafetyCertificateOutlined />, allowed: can('roles', 'READ') },
    { to: 'picklists', label: 'Dropdown values', icon: <TagsOutlined />, allowed: can('settings', 'UPDATE') },
    { to: 'pipelines', label: 'Pipelines', icon: <NodeIndexOutlined />, allowed: can('settings', 'UPDATE') },
    { to: 'audit', label: 'Audit log', icon: <AuditOutlined />, allowed: can('audit', 'READ') },
  ].filter((section) => section.allowed)

  return (
    <div className="with-side">
      <aside className="side-panel">
        <h4>Settings</h4>
        {sections.map((section) => (
          <NavLink key={section.to} to={section.to} className={({ isActive }) => `side-link${isActive ? ' active' : ''}`}>
            {section.icon}
            {section.label}
          </NavLink>
        ))}
      </aside>
      <div>
        <Outlet />
      </div>
    </div>
  )
}

/** Card frame shared by the settings pages; shows a 403 message when the user lacks the permission. */
export function SettingsSection({
  title,
  description,
  extra,
  allowed = true,
  children,
}: {
  title: string
  description?: string
  extra?: ReactNode
  allowed?: boolean
  children: ReactNode
}) {
  if (!allowed) return <Result status="403" title="You don't have access to this section" />
  return (
    <Card
      title={
        <div style={{ padding: '12px 0' }}>
          <div style={{ fontSize: 18 }}>{title}</div>
          {description && <div className="muted" style={{ fontWeight: 400, fontSize: 14, whiteSpace: 'normal' }}>{description}</div>}
        </div>
      }
      extra={extra}
    >
      {children}
    </Card>
  )
}
