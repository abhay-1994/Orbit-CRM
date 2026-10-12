import { CheckCircleFilled } from '@ant-design/icons'
import { Typography } from 'antd'
import type { ReactNode } from 'react'
import { Logo } from '../../layout/Logo'

/** Two-column layout of the sign-in pages: brand panel on the left, form on the right. */
export function AuthShell({ title, subtitle, children }: { title: string; subtitle?: ReactNode; children: ReactNode }) {
  return (
    <div className="auth-shell">
      <aside className="auth-brand">
        <Logo light size={60} />
        <h1>
          Every customer,
          <br />
          in one orbit.
        </h1>
        <p>Leads, contacts, companies, deals and activities in one workspace for your whole sales team.</p>
        <ul>
          <li>
            <CheckCircleFilled />
            Pipeline board with weighted forecasts
          </li>
          <li>
            <CheckCircleFilled />
            Access by role: your own records, your team's or everyone's
          </li>
          <li>
            <CheckCircleFilled />
            Activities, reminders and a shared calendar
          </li>
          <li>
            <CheckCircleFilled />
            CSV import and export with a complete audit trail
          </li>
        </ul>
      </aside>
      <main className="auth-form">
        <div className="auth-card">
          <Logo />
          <h2>{title}</h2>
          {subtitle && <Typography.Paragraph type="secondary">{subtitle}</Typography.Paragraph>}
          {children}
        </div>
      </main>
    </div>
  )
}

export const PASSWORD_HINT = 'At least 10 characters, with letters and digits.'

/** localStorage key remembering the last workspace name typed at sign-in. */
export const LAST_WORKSPACE = 'orbitcrm.workspace'
