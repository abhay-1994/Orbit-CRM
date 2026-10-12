import { LaptopOutlined, SafetyCertificateOutlined, UserOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Avatar, Button, Card, Col, Form, Input, QRCode, Row, Select, Space, Table, Tabs, Tag, Typography } from 'antd'
import { useState } from 'react'
import { http } from '../api/client'
import type { Me, SessionResponse } from '../api/types'
import { useAuth, useMe } from '../auth/context'
import { handleFormError, toast } from '../lib/feedback'
import { formatDateTime, fromNow, initials } from '../lib/format'
import { PASSWORD_HINT } from './auth/AuthShell'
import { confirmRule } from './auth/rules'

export function ProfilePage() {
  const me = useMe()
  return (
    <div>
      <div className="page-head">
        <Avatar size={56} className="avatar-brand">
          {initials(me.fullName)}
        </Avatar>
        <div>
          <h1>{me.fullName}</h1>
          <p>
            {me.email} · {me.role.name}
            {me.team ? ` · ${me.team.name}` : ''}
          </p>
        </div>
      </div>
      <Card>
        <Tabs
          items={[
            { key: 'profile', label: 'Profile', icon: <UserOutlined />, children: <ProfileForm /> },
            { key: 'password', label: 'Password', children: <PasswordForm /> },
            { key: 'mfa', label: 'Two-step login', icon: <SafetyCertificateOutlined />, children: <TwoFactor /> },
            { key: 'sessions', label: 'Devices', icon: <LaptopOutlined />, children: <Sessions /> },
          ]}
        />
      </Card>
    </div>
  )
}

const TIME_ZONES = Intl.supportedValuesOf('timeZone').map((zone) => ({ value: zone, label: zone }))

function ProfileForm() {
  const me = useMe()
  const { updateMe } = useAuth()
  const [form] = Form.useForm()
  const save = useMutation({
    meta: { silent: true },
    mutationFn: (values: Partial<Me>) => http.patch<Me>('/auth/me', values),
    onSuccess: (updated) => {
      updateMe(updated)
      toast.success('Profile saved')
    },
    onError: (error) => handleFormError(form, error),
  })
  return (
    <Form form={form} layout="vertical" initialValues={me} onFinish={(values) => save.mutate(values)} style={{ maxWidth: 640 }}>
      <Row gutter={16}>
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
          <Form.Item name="jobTitle" label="Job title">
            <Input />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="phone" label="Phone">
            <Input />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="timezone" label="Time zone" extra="Used for reminders and 'today' in your lists.">
            <Select showSearch={{ optionFilterProp: 'label' }} options={TIME_ZONES} />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="locale" label="Language and region" extra="For example en-IN or en-US.">
            <Input />
          </Form.Item>
        </Col>
      </Row>
      <Button type="primary" htmlType="submit" loading={save.isPending}>
        Save profile
      </Button>
    </Form>
  )
}

function PasswordForm() {
  const [form] = Form.useForm()
  const change = useMutation({
    meta: { silent: true },
    mutationFn: (values: { currentPassword: string; password: string }) =>
      http.post('/auth/password/change', { currentPassword: values.currentPassword, newPassword: values.password }),
    onSuccess: () => {
      form.resetFields()
      toast.success('Password changed. Your other devices were signed out.')
    },
    onError: (error) => handleFormError(form, error),
  })
  return (
    <Form form={form} layout="vertical" onFinish={(values) => change.mutate(values)} style={{ maxWidth: 420 }}>
      <Form.Item name="currentPassword" label="Current password" rules={[{ required: true }]}>
        <Input.Password autoComplete="current-password" />
      </Form.Item>
      <Form.Item name="password" label="New password" extra={PASSWORD_HINT} rules={[{ required: true, min: 10, message: 'Password is too short' }]}>
        <Input.Password autoComplete="new-password" />
      </Form.Item>
      <Form.Item name="confirm" label="Confirm new password" dependencies={['password']} rules={[{ required: true }, confirmRule]}>
        <Input.Password autoComplete="new-password" />
      </Form.Item>
      <Button type="primary" htmlType="submit" loading={change.isPending}>
        Change password
      </Button>
    </Form>
  )
}

function TwoFactor() {
  const me = useMe()
  const { updateMe } = useAuth()
  const [setup, setSetup] = useState<{ secret: string; otpauthUri: string } | null>(null)
  const [code, setCode] = useState('')

  const begin = useMutation({ mutationFn: () => http.post<{ secret: string; otpauthUri: string }>('/auth/mfa/setup'), onSuccess: setSetup })
  const enable = useMutation({
    mutationFn: () => http.post('/auth/mfa/enable', { code }),
    onSuccess: () => {
      updateMe({ ...me, mfaEnabled: true })
      setSetup(null)
      toast.success('Two-step login is on')
    },
  })
  const disable = useMutation({
    mutationFn: (values: { password: string; code: string }) => http.post('/auth/mfa/disable', values),
    onSuccess: () => {
      updateMe({ ...me, mfaEnabled: false })
      toast.success('Two-step login is off')
    },
  })

  if (me.mfaEnabled) {
    return (
      <div style={{ maxWidth: 460 }}>
        <Alert type="success" showIcon title="Two-step login is on" description="Signing in needs your password and a code from your authenticator app." style={{ marginBottom: 20 }} />
        <Typography.Title level={5}>Turn it off</Typography.Title>
        <Form layout="vertical" onFinish={(values) => disable.mutate(values)}>
          <Form.Item name="password" label="Password" rules={[{ required: true }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="code" label="Current 6-digit code" rules={[{ required: true, len: 6 }]}>
            <Input.OTP length={6} />
          </Form.Item>
          <Button danger htmlType="submit" loading={disable.isPending}>
            Turn off two-step login
          </Button>
        </Form>
      </div>
    )
  }

  if (!setup) {
    return (
      <div style={{ maxWidth: 520 }}>
        <Typography.Paragraph>
          Protect your account with a second step: after your password, enter a 6-digit code from an authenticator app such as Google
          Authenticator or Microsoft Authenticator.
        </Typography.Paragraph>
        <Button type="primary" icon={<SafetyCertificateOutlined />} loading={begin.isPending} onClick={() => begin.mutate()}>
          Set up two-step login
        </Button>
      </div>
    )
  }

  return (
    <Space align="start" size={32} wrap>
      <QRCode value={setup.otpauthUri} size={180} />
      <div style={{ maxWidth: 380 }}>
        <Typography.Paragraph>1. Scan the QR code with your authenticator app, or enter this key:</Typography.Paragraph>
        <Typography.Paragraph code copyable>
          {setup.secret}
        </Typography.Paragraph>
        <Typography.Paragraph>2. Enter the 6-digit code the app shows:</Typography.Paragraph>
        <Input.OTP length={6} value={code} onChange={setCode} />
        <Space style={{ marginTop: 16 }}>
          <Button type="primary" disabled={code.length !== 6} loading={enable.isPending} onClick={() => enable.mutate()}>
            Turn on
          </Button>
          <Button onClick={() => setSetup(null)}>Cancel</Button>
        </Space>
      </div>
    </Space>
  )
}

function describeDevice(userAgent?: string): string {
  if (!userAgent) return 'Unknown device'
  const browser = /Edg\//.test(userAgent) ? 'Edge' : /Chrome\//.test(userAgent) ? 'Chrome' : /Firefox\//.test(userAgent) ? 'Firefox' : /Safari\//.test(userAgent) ? 'Safari' : 'Browser'
  const os = /Windows/.test(userAgent) ? 'Windows' : /Android/.test(userAgent) ? 'Android' : /iPhone|iPad/.test(userAgent) ? 'iOS' : /Mac OS/.test(userAgent) ? 'macOS' : /Linux/.test(userAgent) ? 'Linux' : ''
  return os ? `${browser} on ${os}` : browser
}

function Sessions() {
  const queryClient = useQueryClient()
  const sessions = useQuery({ queryKey: ['sessions'], queryFn: () => http.get<SessionResponse[]>('/auth/sessions') })
  const revoke = useMutation({
    mutationFn: (id: string) => http.delete(`/auth/sessions/${id}`),
    onSuccess: () => {
      toast.success('Device signed out')
      void queryClient.invalidateQueries({ queryKey: ['sessions'] })
    },
  })
  return (
    <Table<SessionResponse>
      rowKey="id"
      loading={sessions.isLoading}
      dataSource={sessions.data}
      pagination={false}
      columns={[
        {
          title: 'Device',
          render: (_, s) => (
            <span>
              {describeDevice(s.userAgent)} {s.current && <Tag color="purple">This device</Tag>}
            </span>
          ),
        },
        { title: 'IP address', dataIndex: 'ipAddress' },
        { title: 'Signed in', render: (_, s) => formatDateTime(s.createdAt) },
        { title: 'Last active', render: (_, s) => fromNow(s.lastUsedAt) },
        {
          title: '',
          render: (_, s) =>
            !s.current && (
              <Button size="small" danger onClick={() => revoke.mutate(s.id)}>
                Sign out
              </Button>
            ),
        },
      ]}
    />
  )
}
