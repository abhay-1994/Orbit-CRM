import { LockOutlined, MailOutlined, TeamOutlined } from '@ant-design/icons'
import { useMutation } from '@tanstack/react-query'
import { Alert, Button, Form, Input } from 'antd'
import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { http } from '../../api/client'
import type { AuthResponse, LoginResponse } from '../../api/types'
import { useAuth } from '../../auth/context'
import { errorText } from '../../lib/feedback'
import { AuthShell, LAST_WORKSPACE } from './AuthShell'

interface Credentials {
  tenant: string
  email: string
  password: string
}

export function LoginPage() {
  const { me, signIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [mfaToken, setMfaToken] = useState<string | null>(null)
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const finish = (auth: AuthResponse) => {
    signIn(auth)
    navigate(from, { replace: true })
  }

  const login = useMutation({
    meta: { silent: true },
    mutationFn: (values: Credentials) => {
      localStorage.setItem(LAST_WORKSPACE, values.tenant)
      return http.post<LoginResponse>('/auth/login', values)
    },
    onSuccess: (response) => (response.mfaRequired ? setMfaToken(response.mfaToken) : finish(response)),
  })
  const verify = useMutation({
    meta: { silent: true },
    mutationFn: (code: string) => http.post<AuthResponse>('/auth/login/mfa', { mfaToken, code }),
    onSuccess: finish,
  })

  if (me) return <Navigate to={from} replace />

  if (mfaToken) {
    return (
      <AuthShell title="Two-step verification" subtitle="Enter the 6-digit code from your authenticator app.">
        {verify.error && <Alert type="error" showIcon title={errorText(verify.error)} style={{ marginBottom: 16 }} />}
        <Form layout="vertical" onFinish={(values: { code: string }) => verify.mutate(values.code)}>
          <Form.Item name="code" rules={[{ required: true, len: 6, message: 'Enter the 6-digit code' }]}>
            <Input.OTP length={6} size="large" />
          </Form.Item>
          <Button type="primary" htmlType="submit" size="large" block loading={verify.isPending}>
            Verify and sign in
          </Button>
          <Button type="link" block onClick={() => setMfaToken(null)} style={{ marginTop: 8 }}>
            Back to sign in
          </Button>
        </Form>
      </AuthShell>
    )
  }

  return (
    <AuthShell title="Sign in to your workspace" subtitle="Welcome back! Use your workspace name, email and password.">
      {login.error && <Alert type="error" showIcon title={errorText(login.error)} style={{ marginBottom: 16 }} />}
      <Form<Credentials>
        layout="vertical"
        requiredMark={false}
        size="large"
        initialValues={{ tenant: localStorage.getItem(LAST_WORKSPACE) ?? '' }}
        onFinish={(values) => login.mutate(values)}
      >
        <Form.Item name="tenant" label="Workspace" rules={[{ required: true, message: 'Enter your workspace name' }]}>
          <Input prefix={<TeamOutlined />} placeholder="e.g. acme" autoComplete="organization" />
        </Form.Item>
        <Form.Item name="email" label="Email" rules={[{ required: true, type: 'email', message: 'Enter a valid email' }]}>
          <Input type="email" prefix={<MailOutlined />} placeholder="you@company.com" autoComplete="email" />
        </Form.Item>
        <Form.Item
          name="password"
          label="Password"
          rules={[{ required: true, message: 'Enter your password' }]}
          extra={<Link to="/forgot-password">Forgot password?</Link>}
        >
          <Input.Password prefix={<LockOutlined />} autoComplete="current-password" />
        </Form.Item>
        <Button type="primary" htmlType="submit" block loading={login.isPending}>
          Sign in
        </Button>
      </Form>
      <p style={{ marginTop: 24, textAlign: 'center' }}>
        New to OrbitCRM? <Link to="/register">Create a workspace</Link>
      </p>
    </AuthShell>
  )
}
