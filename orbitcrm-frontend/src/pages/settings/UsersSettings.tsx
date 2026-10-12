import { MailOutlined, PlusOutlined } from '@ant-design/icons'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Avatar, Button, Col, Form, Input, Modal, Row, Select, Space, Table, Tag } from 'antd'
import { useState } from 'react'
import { http, qs } from '../../api/client'
import type { PageResponse, RoleResponse, TeamResponse, UserResponse, UserStatus } from '../../api/types'
import { handleFormError, toast } from '../../lib/feedback'
import { fromNow, initials } from '../../lib/format'
import { useCan } from '../../lib/hooks'
import { SettingsSection } from './SettingsLayout'

const STATUS_COLORS: Record<UserStatus, string> = { INVITED: 'gold', ACTIVE: 'green', DISABLED: 'default' }

export function UsersSettings() {
  const can = useCan()
  const queryClient = useQueryClient()
  const [q, setQ] = useState('')
  const [status, setStatus] = useState<UserStatus>()
  const [paging, setPaging] = useState({ page: 0, size: 25 })
  const [editing, setEditing] = useState<UserResponse | 'new' | null>(null)

  const users = useQuery({
    queryKey: ['users', q, status, paging],
    queryFn: () => http.get<PageResponse<UserResponse>>(`/users${qs({ q, status, page: paging.page, size: paging.size })}`),
    enabled: can('users', 'READ'),
    placeholderData: keepPreviousData,
  })
  const resend = useMutation({
    mutationFn: (id: string) => http.post(`/users/${id}/resend-invitation`),
    onSuccess: () => toast.success('Invitation sent again'),
  })

  return (
    <SettingsSection
      title="Users"
      description="Invite colleagues and manage their roles, teams and access."
      allowed={can('users', 'READ')}
      extra={
        can('users', 'CREATE') && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing('new')}>
            Invite user
          </Button>
        )
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <Input.Search placeholder="Search name or email" allowClear onSearch={(v) => setQ(v.trim())} style={{ width: 260 }} />
        <Select
          allowClear
          placeholder="Any status"
          style={{ width: 150 }}
          value={status}
          onChange={setStatus}
          options={(['ACTIVE', 'INVITED', 'DISABLED'] as UserStatus[]).map((s) => ({ value: s, label: s.charAt(0) + s.slice(1).toLowerCase() }))}
        />
      </Space>
      <Table<UserResponse>
        rowKey="id"
        loading={users.isFetching}
        dataSource={users.data?.content}
        pagination={{
          current: paging.page + 1,
          pageSize: paging.size,
          total: users.data?.totalElements,
          onChange: (page, size) => setPaging({ page: page - 1, size }),
        }}
        columns={[
          {
            title: 'User',
            render: (_, u) => (
              <Space>
                <Avatar className="avatar-brand">{initials(u.fullName)}</Avatar>
                <div>
                  <strong>{u.fullName}</strong>
                  <div className="muted" style={{ fontSize: 12 }}>
                    {u.email}
                  </div>
                </div>
              </Space>
            ),
          },
          { title: 'Role', dataIndex: 'roleName' },
          { title: 'Team', render: (_, u) => u.teamName ?? <span className="muted">—</span> },
          {
            title: 'Status',
            render: (_, u) => (
              <Space size={4}>
                <Tag color={STATUS_COLORS[u.status]}>{u.status.toLowerCase()}</Tag>
                {u.mfaEnabled && <Tag color="purple">2-step</Tag>}
              </Space>
            ),
          },
          { title: 'Last sign-in', render: (_, u) => (u.lastLoginAt ? fromNow(u.lastLoginAt) : <span className="muted">never</span>) },
          {
            title: '',
            render: (_, u) =>
              can('users', 'UPDATE') && (
                <Space>
                  <Button size="small" onClick={() => setEditing(u)}>
                    Edit
                  </Button>
                  {u.status === 'INVITED' && (
                    <Button size="small" icon={<MailOutlined />} onClick={() => resend.mutate(u.id)}>
                      Resend invite
                    </Button>
                  )}
                </Space>
              ),
          },
        ]}
      />
      {editing && (
        <UserModal
          user={editing === 'new' ? undefined : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            void queryClient.invalidateQueries({ queryKey: ['users'] })
          }}
        />
      )}
    </SettingsSection>
  )
}

function UserModal({ user, onClose, onSaved }: { user?: UserResponse; onClose: () => void; onSaved: () => void }) {
  const can = useCan()
  const [form] = Form.useForm()
  const roles = useQuery({ queryKey: ['roles'], queryFn: () => http.get<RoleResponse[]>('/roles'), enabled: can('roles', 'READ') })
  const teams = useQuery({ queryKey: ['teams'], queryFn: () => http.get<TeamResponse[]>('/teams') })

  const save = useMutation({
    meta: { silent: true },
    mutationFn: (values: Record<string, unknown>) =>
      user
        ? http.patch(`/users/${user.id}`, { ...values, clearTeam: !values.teamId })
        : http.post('/users', values),
    onSuccess: () => {
      toast.success(user ? 'User updated' : 'Invitation sent')
      onSaved()
    },
    onError: (error) => handleFormError(form, error),
  })

  return (
    <Modal open title={user ? `Edit ${user.fullName}` : 'Invite a user'} okText={user ? 'Save' : 'Send invitation'} onOk={() => form.submit()} confirmLoading={save.isPending} onCancel={onClose} destroyOnHidden width={600}>
      <Form form={form} layout="vertical" initialValues={user} onFinish={(values) => save.mutate(values)}>
        <Row gutter={16}>
          {!user && (
            <Col span={24}>
              <Form.Item name="email" label="Email" rules={[{ required: true, type: 'email' }]} extra="They will receive a link to set their password (valid for 7 days).">
                <Input />
              </Form.Item>
            </Col>
          )}
          <Col span={12}>
            <Form.Item name="firstName" label="First name" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="lastName" label="Last name" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="roleId" label="Role" rules={[{ required: true }]}>
              <Select options={roles.data?.map((r) => ({ value: r.id, label: r.name }))} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="teamId" label="Team">
              <Select allowClear placeholder="No team" options={teams.data?.map((t) => ({ value: t.id, label: t.name }))} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="jobTitle" label="Job title">
              <Input />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="phone" label="Phone">
              <Input />
            </Form.Item>
          </Col>
          {user && user.status !== 'INVITED' && (
            <Col span={12}>
              <Form.Item name="status" label="Status" extra="Disabling signs the user out everywhere.">
                <Select options={[{ value: 'ACTIVE', label: 'Active' }, { value: 'DISABLED', label: 'Disabled' }]} />
              </Form.Item>
            </Col>
          )}
        </Row>
      </Form>
    </Modal>
  )
}
