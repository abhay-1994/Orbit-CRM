import { CheckSquareOutlined, ClockCircleOutlined, FlagOutlined, PhoneOutlined, VideoCameraOutlined } from '@ant-design/icons'
import type { ReactNode } from 'react'
import { brand } from '../theme'

/** Icon and colour per activity type, shared by the dashboard, calendar and timeline. */
export const ACTIVITY_STYLE: Record<string, { icon: ReactNode; color: string }> = {
  meeting: { icon: <VideoCameraOutlined />, color: brand.purple },
  call: { icon: <PhoneOutlined />, color: '#0b93ad' },
  task: { icon: <CheckSquareOutlined />, color: brand.navy },
  event: { icon: <FlagOutlined />, color: '#5b29c7' },
  reminder: { icon: <ClockCircleOutlined />, color: '#0f6f86' },
}

export const OPEN_ACTIVITY_STATUSES = ['not_started', 'in_progress']
