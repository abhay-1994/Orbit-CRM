import { AimOutlined, BankOutlined, CalendarOutlined, ContactsOutlined, DollarOutlined } from '@ant-design/icons'
import type { ReactNode } from 'react'

export interface ModuleInfo {
  key: string
  label: string
  singular: string
  icon: ReactNode
}

/** The CRM modules in navigation order. */
export const MODULES: ModuleInfo[] = [
  { key: 'leads', label: 'Leads', singular: 'Lead', icon: <AimOutlined /> },
  { key: 'contacts', label: 'Contacts', singular: 'Contact', icon: <ContactsOutlined /> },
  { key: 'companies', label: 'Companies', singular: 'Company', icon: <BankOutlined /> },
  { key: 'deals', label: 'Deals', singular: 'Deal', icon: <DollarOutlined /> },
  { key: 'activities', label: 'Activities', singular: 'Activity', icon: <CalendarOutlined /> },
]

export const MODULE_BY_KEY: Record<string, ModuleInfo> = Object.fromEntries(MODULES.map((m) => [m.key, m]))
