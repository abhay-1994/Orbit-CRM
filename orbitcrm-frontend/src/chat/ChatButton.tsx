import { MessageOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Badge, Button, Tooltip } from 'antd'
import { useEffect } from 'react'
import { useNavigate } from 'react-router'
import { http } from '../api/client'
import { chatKeys } from './cache'

const TITLE = document.title

/** Header button to the chat page with the unread badge; the count is also shown in the browser tab. */
export function ChatButton() {
  const navigate = useNavigate()
  const unread = useQuery({
    queryKey: chatKeys.unread,
    queryFn: () => http.get<{ unread: number }>('/chat/unread-count'),
    refetchInterval: 60_000,
  })
  const count = unread.data?.unread ?? 0

  useEffect(() => {
    document.title = count ? `(${count}) ${TITLE}` : TITLE
    return () => {
      document.title = TITLE
    }
  }, [count])

  return (
    <Tooltip title="Chat">
      <Badge count={count} size="small" offset={[-4, 4]}>
        <Button
          type="text"
          shape="circle"
          icon={<MessageOutlined style={{ fontSize: 18 }} />}
          aria-label={count ? `Chat, ${count} unread` : 'Chat'}
          onClick={() => navigate('/chat')}
        />
      </Badge>
    </Tooltip>
  )
}
