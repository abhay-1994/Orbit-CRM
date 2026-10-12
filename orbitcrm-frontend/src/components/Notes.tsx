import { DeleteOutlined, EditOutlined, PushpinFilled, PushpinOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Avatar, Button, Empty, Input, Popconfirm, Space, Tag, Tooltip, Typography } from 'antd'
import { useState } from 'react'
import { http } from '../api/client'
import type { NoteView, PageResponse } from '../api/types'
import { useMe } from '../auth/context'
import { fromNow, initials } from '../lib/format'
import { UserSelect } from './pickers'

/** Notes on a record: pinned first, @mentions notify colleagues, authors can edit their own. */
export function Notes({ module, recordId }: { module: string; recordId: string }) {
  const me = useMe()
  const queryClient = useQueryClient()
  const [body, setBody] = useState('')
  const [mentions, setMentions] = useState<string[]>([])
  const [editing, setEditing] = useState<{ id: string; body: string } | null>(null)

  const notes = useQuery({
    queryKey: ['notes', module, recordId],
    queryFn: () => http.get<PageResponse<NoteView>>(`/${module}/${recordId}/notes?size=100`),
  })
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['notes', module, recordId] })
    void queryClient.invalidateQueries({ queryKey: ['timeline', module, recordId] })
  }

  const add = useMutation({
    mutationFn: () => http.post(`/${module}/${recordId}/notes`, { body, mentionedUserIds: mentions }),
    onSuccess: () => {
      setBody('')
      setMentions([])
      refresh()
    },
  })
  const update = useMutation({
    mutationFn: ({ id, ...changes }: { id: string; body?: string; pinned?: boolean }) => http.patch(`/notes/${id}`, changes),
    onSuccess: () => {
      setEditing(null)
      refresh()
    },
  })
  const remove = useMutation({ mutationFn: (id: string) => http.delete(`/notes/${id}`), onSuccess: refresh })

  return (
    <div>
      <Input.TextArea
        value={body}
        onChange={(e) => setBody(e.target.value)}
        placeholder="Write a note…"
        autoSize={{ minRows: 3, maxRows: 10 }}
        maxLength={20000}
      />
      <div style={{ display: 'flex', gap: 8, margin: '8px 0 20px' }}>
        <UserSelect<string[]> multiple value={mentions} onChange={setMentions} placeholder="Mention colleagues (they get notified)" style={{ flex: 1 }} />
        <Button type="primary" disabled={!body.trim()} loading={add.isPending} onClick={() => add.mutate()}>
          Add note
        </Button>
      </div>

      {notes.data?.content.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No notes yet" />}
      {notes.data?.content.map((note) => {
        const mine = note.createdBy === me.id || me.administrator
        return (
          <div key={note.id} style={{ display: 'flex', gap: 12, padding: '12px 0', borderTop: '1px solid #f0f1f6' }}>
            <Avatar className="avatar-brand">{initials(note.createdByName)}</Avatar>
            <div style={{ flex: 1, minWidth: 0 }}>
              <Space size={8}>
                <strong>{note.createdByName}</strong>
                <span className="muted">{fromNow(note.createdAt)}</span>
                {note.pinned && <Tag color="purple">Pinned</Tag>}
              </Space>
              {editing?.id === note.id ? (
                <div style={{ marginTop: 6 }}>
                  <Input.TextArea value={editing.body} onChange={(e) => setEditing({ id: note.id, body: e.target.value })} autoSize={{ minRows: 2 }} />
                  <Space style={{ marginTop: 6 }}>
                    <Button size="small" type="primary" loading={update.isPending} onClick={() => update.mutate({ id: note.id, body: editing.body })}>
                      Save
                    </Button>
                    <Button size="small" onClick={() => setEditing(null)}>
                      Cancel
                    </Button>
                  </Space>
                </div>
              ) : (
                <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', margin: '4px 0 0' }}>{note.body}</Typography.Paragraph>
              )}
            </div>
            {mine && editing?.id !== note.id && (
              <Space size={0}>
                <Tooltip title={note.pinned ? 'Unpin' : 'Pin to top'}>
                  <Button type="text" size="small" icon={note.pinned ? <PushpinFilled /> : <PushpinOutlined />} onClick={() => update.mutate({ id: note.id, pinned: !note.pinned })} />
                </Tooltip>
                <Button type="text" size="small" icon={<EditOutlined />} onClick={() => setEditing({ id: note.id, body: note.body })} aria-label="Edit note" />
                <Popconfirm title="Delete this note?" onConfirm={() => remove.mutate(note.id)}>
                  <Button type="text" size="small" danger icon={<DeleteOutlined />} aria-label="Delete note" />
                </Popconfirm>
              </Space>
            )}
          </div>
        )
      })}
    </div>
  )
}
