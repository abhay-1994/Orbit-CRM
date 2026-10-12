import { PlusOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, Checkbox, Drawer, Form, Input, Popconfirm, Select, Space, Table, Tag } from 'antd'
import { useState } from 'react'
import { http } from '../../api/client'
import type { AccessScope, Action, CatalogResource, PermissionCatalog, Permissions, RoleResponse } from '../../api/types'
import { handleFormError, toast } from '../../lib/feedback'
import { useCan } from '../../lib/hooks'
import { SettingsSection } from './SettingsLayout'

const ACTIONS: Action[] = ['CREATE', 'READ', 'UPDATE', 'DELETE', 'EXPORT', 'IMPORT']
const SCOPE_OPTIONS: { value: AccessScope; label: string }[] = [
  { value: 'NONE', label: 'No access' },
  { value: 'OWN', label: 'Own records' },
  { value: 'TEAM', label: 'Team records' },
  { value: 'ALL', label: 'All records' },
]

export function RolesSettings() {
  const can = useCan()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<RoleResponse | 'new' | null>(null)
  const roles = useQuery({ queryKey: ['roles'], queryFn: () => http.get<RoleResponse[]>('/roles'), enabled: can('roles', 'READ') })
  const remove = useMutation({
    mutationFn: (id: string) => http.delete(`/roles/${id}`),
    onSuccess: () => {
      toast.success('Role deleted')
      void queryClient.invalidateQueries({ queryKey: ['roles'] })
    },
  })

  return (
    <SettingsSection
      title="Roles & permissions"
      description="A role decides what its users can do in each area, and whether that covers their own records, their team's or everyone's. Changes apply immediately."
      allowed={can('roles', 'READ')}
      extra={
        can('roles', 'CREATE') && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing('new')}>
            New role
          </Button>
        )
      }
    >
      <Table<RoleResponse>
        rowKey="id"
        loading={roles.isLoading}
        dataSource={roles.data}
        pagination={false}
        columns={[
          {
            title: 'Role',
            render: (_, r) => (
              <Space>
                <strong>{r.name}</strong>
                {r.administrator && <Tag color="purple">Full access</Tag>}
                {r.system && <Tag>Built-in</Tag>}
              </Space>
            ),
          },
          { title: 'Description', render: (_, r) => <span className="muted">{r.description}</span> },
          { title: 'Users', dataIndex: 'userCount', width: 90 },
          {
            title: '',
            render: (_, r) => (
              <Space>
                <Button size="small" onClick={() => setEditing(r)}>
                  {r.administrator || !can('roles', 'UPDATE') ? 'View' : 'Edit'}
                </Button>
                {!r.system && can('roles', 'DELETE') && (
                  <Popconfirm title={`Delete ${r.name}?`} description="Only roles without users can be deleted." onConfirm={() => remove.mutate(r.id)}>
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
        <RoleEditor
          role={editing === 'new' ? undefined : editing}
          readOnly={editing !== 'new' && (editing.administrator || !can('roles', 'UPDATE'))}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            void queryClient.invalidateQueries({ queryKey: ['roles'] })
          }}
        />
      )}
    </SettingsSection>
  )
}

function RoleEditor({ role, readOnly, onClose, onSaved }: { role?: RoleResponse; readOnly: boolean; onClose: () => void; onSaved: () => void }) {
  const [form] = Form.useForm()
  const [permissions, setPermissions] = useState<Permissions>(role?.permissions ?? {})
  const catalog = useQuery({ queryKey: ['permission-catalog'], queryFn: () => http.get<PermissionCatalog>('/roles/permission-catalog') })

  const save = useMutation({
    meta: { silent: true },
    mutationFn: (values: { name: string; description?: string }) =>
      role ? http.put(`/roles/${role.id}`, { ...values, permissions }) : http.post('/roles', { ...values, permissions }),
    onSuccess: () => {
      toast.success(role ? 'Role saved' : 'Role created')
      onSaved()
    },
    onError: (error) => handleFormError(form, error),
  })

  const setScope = (resource: string, action: Action, scope: AccessScope) =>
    setPermissions({ ...permissions, [resource]: { ...permissions[resource], [action]: scope } })

  const cell = (resource: CatalogResource, action: Action) => {
    if (!resource.actions.includes(action)) return <span className="muted">—</span>
    const scope = role?.administrator ? 'ALL' : (permissions[resource.key]?.[action] ?? 'NONE')
    // Creating and importing, and every action on admin areas, are simply allowed or not.
    if (!resource.scoped || action === 'CREATE' || action === 'IMPORT') {
      return <Checkbox checked={scope !== 'NONE'} disabled={readOnly} onChange={(e) => setScope(resource.key, action, e.target.checked ? 'ALL' : 'NONE')} />
    }
    return <Select size="small" style={{ width: 132 }} value={scope} disabled={readOnly} options={SCOPE_OPTIONS} onChange={(s) => setScope(resource.key, action, s)} />
  }

  return (
    <Drawer
      open
      size={1000}
      title={role ? role.name : 'New role'}
      onClose={onClose}
      destroyOnHidden
      footer={
        !readOnly && (
          <Space style={{ float: 'right' }}>
            <Button onClick={onClose}>Cancel</Button>
            <Button type="primary" loading={save.isPending} onClick={() => form.submit()}>
              Save role
            </Button>
          </Space>
        )
      }
    >
      {role?.administrator && <Alert type="info" showIcon title="The Administrator role always has full access and cannot be changed." style={{ marginBottom: 16 }} />}
      <Form form={form} layout="vertical" initialValues={role} disabled={readOnly} onFinish={(values) => save.mutate(values)}>
        <Space align="start" wrap>
          <Form.Item name="name" label="Name" rules={[{ required: true, max: 80 }]}>
            <Input style={{ width: 280 }} />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input style={{ width: 520 }} />
          </Form.Item>
        </Space>
      </Form>
      <Table<CatalogResource>
        rowKey="key"
        size="small"
        pagination={false}
        loading={catalog.isLoading}
        dataSource={catalog.data?.resources}
        columns={[
          {
            title: 'Area',
            render: (_, r) => (
              <span>
                <strong>{r.label}</strong> {r.category === 'system' && <Tag>Admin</Tag>}
              </span>
            ),
          },
          ...ACTIONS.map((action) => ({
            title: action.charAt(0) + action.slice(1).toLowerCase(),
            key: action,
            render: (_: unknown, r: CatalogResource) => cell(r, action),
          })),
        ]}
      />
    </Drawer>
  )
}
