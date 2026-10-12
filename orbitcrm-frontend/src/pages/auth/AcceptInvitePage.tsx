import { useMutation } from '@tanstack/react-query'
import { Alert, Button, Form, Input } from 'antd'
import { useNavigate, useSearchParams } from 'react-router'
import { http } from '../../api/client'
import type { AuthResponse } from '../../api/types'
import { useAuth } from '../../auth/context'
import { errorText } from '../../lib/feedback'
import { AuthShell, PASSWORD_HINT } from './AuthShell'
import { confirmRule } from './rules'

/** Opened from the invitation email: /accept-invite?token=... */
export function AcceptInvitePage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const navigate = useNavigate()
  const { signIn } = useAuth()

  const accept = useMutation({
    meta: { silent: true },
    mutationFn: (password: string) => http.post<AuthResponse>('/auth/invitations/accept', { token, password }),
    onSuccess: (auth) => {
      signIn(auth)
      navigate('/', { replace: true })
    },
  })

  return (
    <AuthShell title="Join your team on OrbitCRM" subtitle={`Choose a password to activate your account. ${PASSWORD_HINT}`}>
      {!token && <Alert type="warning" showIcon title="This link is incomplete. Open the link from the invitation email again." />}
      {accept.error && <Alert type="error" showIcon title={errorText(accept.error)} style={{ marginBottom: 16 }} />}
      <Form layout="vertical" requiredMark={false} size="large" onFinish={(values: { password: string }) => accept.mutate(values.password)}>
        <Form.Item name="password" label="Password" rules={[{ required: true, min: 10, message: PASSWORD_HINT }]}>
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Form.Item name="confirm" label="Confirm password" dependencies={['password']} rules={[{ required: true, message: 'Repeat the password' }, confirmRule]}>
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Button type="primary" htmlType="submit" block loading={accept.isPending} disabled={!token}>
          Activate account
        </Button>
      </Form>
    </AuthShell>
  )
}
