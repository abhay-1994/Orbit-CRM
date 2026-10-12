import { DownOutlined, LogoutOutlined, UserOutlined } from '@ant-design/icons'
import { Dropdown } from 'antd'
import { useNavigate } from 'react-router'
import type { PresenceStatus } from '../api/types'
import { useAuth, useMe } from '../auth/context'
import { useChat } from '../chat/context'
import { PresenceAvatar, PresenceDot } from '../chat/presence'
import { PRESENCE, STATUS_CHOICES } from '../chat/statuses'

const STATUS_KEY = 'status:'

/** The user chip: presence, a Teams-style status picker, profile and sign out. */
export function UserMenu() {
  const me = useMe()
  const { signOut } = useAuth()
  const { myStatus, chooseStatus } = useChat()
  const navigate = useNavigate()

  return (
    <Dropdown
      trigger={['click']}
      menu={{
        items: [
          {
            key: 'status',
            icon: <PresenceDot status={myStatus} />,
            label: PRESENCE[myStatus].label,
            children: STATUS_CHOICES.map((choice) => ({
              key: STATUS_KEY + choice.status,
              icon: <PresenceDot status={choice.status} />,
              label: choice.label,
            })),
          },
          { type: 'divider' },
          { key: 'profile', icon: <UserOutlined />, label: 'My profile' },
          { type: 'divider' },
          { key: 'logout', icon: <LogoutOutlined />, label: 'Sign out', danger: true },
        ],
        onClick: ({ key }) => {
          if (key.startsWith(STATUS_KEY)) void chooseStatus(key.slice(STATUS_KEY.length) as PresenceStatus)
          else if (key === 'logout') void signOut()
          else navigate('/profile')
        },
      }}
    >
      <button className="user-chip" type="button" aria-label={`${me.fullName}, ${PRESENCE[myStatus].label}`}>
        <PresenceAvatar name={me.fullName} status={myStatus} size={32} />
        <span className="user-chip-text">
          <strong>{me.fullName}</strong>
          <small>{me.role.name}</small>
        </span>
        <DownOutlined style={{ fontSize: 11, color: '#6b7394' }} />
      </button>
    </Dropdown>
  )
}
