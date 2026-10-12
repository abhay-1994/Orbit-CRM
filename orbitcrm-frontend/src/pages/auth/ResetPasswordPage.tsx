import { useMutation } from '@tanstack/react-query'
import { Alert, Button, Form, Input, Result } from 'antd'
import { Link, useSearchParams } from 'react-router'
import { http } from '../../api/client'
import { errorText } from '../../lib/feedback'
import { AuthShell, PASSWORD_HINT } from './AuthShell'
import { confirmRule } from './rules'

/** Opened from the reset email: /reset-password?token=... */
export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const reset = useMutation({
    meta: { silent: true },
    mutationFn: (newPassword: string) => http.post('/auth/password/reset', { token, newPassword }),
  })

  if (reset.isSuccess) {
    return (
      <AuthShell title="Password updated">
        <Result
          status="success"
          title="Your password has been reset"
          subTitle="For your security, you have been signed out on all devices."
          extra={
            <Link to="/login">
              <Button type="primary">Sign in</Button>
            </Link>
          }
        />
      </AuthShell>
    )
  }

  return (
    <AuthShell title="Choose a new password" subtitle={PASSWORD_HINT}>
      {!token && <Alert type="warning" showIcon title="This link is incomplete. Open the link from the email again." />}
      {reset.error && <Alert type="error" showIcon title={errorText(reset.error)} style={{ marginBottom: 16 }} />}
      <Form layout="vertical" requiredMark={false} size="large" onFinish={(values: { password: string }) => reset.mutate(values.password)}>
        <Form.Item name="password" label="New password" rules={[{ required: true, min: 10, message: PASSWORD_HINT }]}>
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Form.Item name="confirm" label="Confirm password" dependencies={['password']} rules={[{ required: true, message: 'Repeat the password' }, confirmRule]}>
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Button type="primary" htmlType="submit" block loading={reset.isPending} disabled={!token}>
          Reset password
        </Button>
      </Form>
    </AuthShell>
  )
}
