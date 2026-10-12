import { Avatar } from 'antd'
import type { PresenceStatus } from '../api/types'
import { initials } from '../lib/format'
import { PRESENCE } from './statuses'

export function PresenceDot({ status, size = 12 }: { status: PresenceStatus; size?: number }) {
  const { label, color, glyph } = PRESENCE[status]
  return (
    <svg className="presence-dot" width={size} height={size} viewBox="0 0 16 16" role="img" aria-label={label}>
      <title>{label}</title>
      {status === 'OFFLINE' ? (
        <>
          <circle cx="8" cy="8" r="7" fill="#fff" />
          <circle cx="8" cy="8" r="5.9" fill="none" stroke={color} strokeWidth="1.6" />
          <path d="m5.9 5.9 4.2 4.2m0-4.2-4.2 4.2" stroke={color} strokeWidth="1.5" strokeLinecap="round" />
        </>
      ) : (
        <>
          <circle cx="8" cy="8" r="7" fill={color} />
          {glyph}
        </>
      )}
    </svg>
  )
}

/** Initials avatar with the person's presence dot in the corner. */
export function PresenceAvatar({ name, status, size = 36 }: { name: string; status?: PresenceStatus; size?: number }) {
  return (
    <span className="presence-avatar">
      <Avatar className="avatar-brand" size={size}>
        {initials(name)}
      </Avatar>
      {status && <PresenceDot status={status} size={Math.max(10, Math.round(size * 0.36))} />}
    </span>
  )
}
