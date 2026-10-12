import type { ReactNode } from 'react'
import type { PresenceStatus } from '../api/types'

const GREEN = '#6bb700'
const RED = '#c4314b'
const AMBER = '#f5a524'
const GREY = '#8a8886'

const WHITE_STROKE = { stroke: '#fff', strokeWidth: 1.8, strokeLinecap: 'round', strokeLinejoin: 'round', fill: 'none' } as const

/** Label, colour and inner glyph of each status (modelled on Microsoft Teams). */
export const PRESENCE: Record<PresenceStatus, { label: string; color: string; glyph?: ReactNode }> = {
  AVAILABLE: { label: 'Available', color: GREEN, glyph: <path d="M4.6 8.3 7 10.6l4.4-4.8" {...WHITE_STROKE} /> },
  BUSY: { label: 'Busy', color: RED },
  IN_A_MEETING: { label: 'In a meeting', color: RED },
  DO_NOT_DISTURB: { label: 'Do not disturb', color: RED, glyph: <rect x="4" y="7" width="8" height="2" rx="1" fill="#fff" /> },
  BE_RIGHT_BACK: { label: 'Be right back', color: AMBER, glyph: <path d="M10.8 8H5.4m2.1-2.3L5.2 8l2.3 2.3" {...WHITE_STROKE} /> },
  AWAY: { label: 'Away', color: AMBER, glyph: <path d="M8 4.6v3.6l2.3 1.4" {...WHITE_STROKE} /> },
  OFFLINE: { label: 'Offline', color: GREY },
}

/** Statuses a person can choose, as worded in the status menu. Available returns to automatic presence. */
export const STATUS_CHOICES: { status: PresenceStatus; label: string }[] = [
  { status: 'AVAILABLE', label: 'Available' },
  { status: 'BUSY', label: 'Busy' },
  { status: 'DO_NOT_DISTURB', label: 'Do not disturb' },
  { status: 'BE_RIGHT_BACK', label: 'Be right back' },
  { status: 'AWAY', label: 'Appear away' },
  { status: 'OFFLINE', label: 'Appear offline' },
]
