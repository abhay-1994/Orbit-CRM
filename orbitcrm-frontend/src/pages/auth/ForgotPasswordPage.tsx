import { useMutation } from '@tanstack/react-query'
import { Button, Form, Input, Result } from 'antd'
import { Link } from 'react-router'
import { http } from '../../api/client'
import { AuthShell, LAST_WORKSPACE } from './AuthShell'

export function ForgotPasswordPage() {
  const request = useMutation({
    mutationFn: (values: { tenant: string; email: string }) => http.post('/auth/password/forgot', values),
  })

  if (request.isSuccess) {
    return (
      <AuthShell title="Check your inbox">
        <Result
          status="success"
          title="Reset link sent"
          subTitle="If an account exists for that email, we've sent a link to reset your password. It expires in 1 hour."
          extra={<Link to="/login">Back to sign in</Link>}
        />
      </AuthShell>
    )
  }

  return (
    <AuthShell title="Forgot your password?" subtitle="Enter your workspace and email, and we'll send you a reset link.">
      <Form
        layout="vertical"
        requiredMark={false}
        size="large"
        initialValues={{ tenant: localStorage.getItem(LAST_WORKSPACE) ?? '' }}
        onFinish={(values) => request.mutate(values)}
      >
        <Form.Item name="tenant" label="Workspace" rules={[{ required: true, message: 'Enter your workspace name' }]}>
          <Input placeholder="e.g. acme" />
        </Form.Item>
        <Form.Item name="email" label="Email" rules={[{ required: true, type: 'email', message: 'Enter a valid email' }]}>
          <Input type="email" autoComplete="email" />
        </Form.Item>
        <Button type="primary" htmlType="submit" block loading={request.isPending}>
          Send reset link
        </Button>
      </Form>
      <p style={{ marginTop: 24, textAlign: 'center' }}>
        <Link to="/login">Back to sign in</Link>
      </p>
    </AuthShell>
  )
}
