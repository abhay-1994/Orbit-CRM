import { PlusOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Form, Input, Modal, Popconfirm, Select, Space, Table } from 'antd'
import { useState } from 'react'
import { http } from '../../api/client'
import type { TeamResponse } from '../../api/types'
import { handleFormError, toast } from '../../lib/feedback'
import { useCan } from '../../lib/hooks'
import { SettingsSection } from './SettingsLayout'

export function TeamsSettings() {
  const can = useCan()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<TeamResponse | 'new' | null>(null)
  const teams = useQuery({ queryKey: ['teams'], queryFn: () => http.get<TeamResponse[]>('/teams') })
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['teams'] })
  const remove = useMutation({
    mutationFn: (id: string) => http.delete(`/teams/${id}`),
    onSuccess: () => {
      toast.success('Team deleted')
      refresh()
    },
  })
  const nameOf = (id?: string) => teams.data?.find((t) => t.id === id)?.name

  return (
    <SettingsSection
      title="Teams"
      description="Teams can be nested (e.g. South Region → Bengaluru). Users with 'team' access see records of their team and its sub-teams."
      extra={
        can('teams', 'CREATE') && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing('new')}>
            New team
          </Button>
        )
      }
    >
      <Table<TeamResponse>
        rowKey="id"
        loading={teams.isLoading}
        dataSource={teams.data}
        pagination={false}
        columns={[
          { title: 'Team', render: (_, t) => <strong>{t.name}</strong> },
          { title: 'Part of', render: (_, t) => nameOf(t.parentId) ?? <span className="muted">—</span> },
          { title: 'Members', dataIndex: 'memberCount' },
          { title: 'Description', render: (_, t) => t.description ?? '' },
          {
            title: '',
            render: (_, t) => (
              <Space>
                {can('teams', 'UPDATE') && (
                  <Button size="small" onClick={() => setEditing(t)}>
                    Edit
                  </Button>
                )}
                {can('teams', 'DELETE') && (
                  <Popconfirm title={`Delete ${t.name}?`} description="Only empty teams without sub-teams can be deleted." onConfirm={() => remove.mutate(t.id)}>
                    <Button size="small" danger>
                      Delete
                    </Button>
                  </Popconfirm>
                )}
              </Space>
            ),
          },
        ]}
      />
      {editing && (
        <TeamModal
          team={editing === 'new' ? undefined : editing}
          teams={teams.data ?? []}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            refresh()
          }}
        />
      )}
    </SettingsSection>
  )
}

function TeamModal({ team, teams, onClose, onSaved }: { team?: TeamResponse; teams: TeamResponse[]; onClose: () => void; onSaved: () => void }) {
  const [form] = Form.useForm()
  const save = useMutation({
    meta: { silent: true },
    mutationFn: (values: Record<string, unknown>) => (team ? http.put(`/teams/${team.id}`, values) : http.post('/teams', values)),
    onSuccess: () => {
      toast.success(team ? 'Team updated' : 'Team created')
      onSaved()
    },
    onError: (error) => handleFormError(form, error),
  })
  return (
    <Modal open title={team ? `Edit ${team.name}` : 'New team'} onOk={() => form.submit()} confirmLoading={save.isPending} onCancel={onClose} destroyOnHidden>
      <Form form={form} layout="vertical" initialValues={team} onFinish={(values) => save.mutate(values)}>
        <Form.Item name="name" label="Name" rules={[{ required: true, max: 120 }]}>
          <Input />
        </Form.Item>
        <Form.Item name="parentId" label="Part of">
          <Select allowClear placeholder="Top-level team" options={teams.filter((t) => t.id !== team?.id).map((t) => ({ value: t.id, label: t.name }))} />
        </Form.Item>
        <Form.Item name="description" label="Description">
          <Input.TextArea autoSize={{ minRows: 2 }} />
        </Form.Item>
      </Form>
    </Modal>
  )
}
