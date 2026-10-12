import { useMutation, useQuery } from '@tanstack/react-query'
import { Button, Col, Form, Input, Row } from 'antd'
import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { http, qs } from '../../api/client'
import type { AuthResponse } from '../../api/types'
import { useAuth } from '../../auth/context'
import { handleFormError } from '../../lib/feedback'
import { useDebounced } from '../../lib/hooks'
import { AuthShell, PASSWORD_HINT } from './AuthShell'

interface Signup {
  companyName: string
  tenantSlug: string
  firstName: string
  lastName: string
  email: string
  password: string
}

const toSlug = (name: string) =>
  name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 40)

export function RegisterPage() {
  const { me, signIn } = useAuth()
  const navigate = useNavigate()
  const [form] = Form.useForm<Signup>()
  // True once the user types in the workspace field; until then it follows the company name.
  const [slugEdited, setSlugEdited] = useState(false)
  const slug = useDebounced(Form.useWatch('tenantSlug', form) ?? '', 400)

  const availability = useQuery({
    queryKey: ['slug-availability', slug],
    queryFn: () => http.get<{ available: boolean }>(`/auth/slug-availability${qs({ slug })}`),
    enabled: slug.length >= 3,
  })

  const register = useMutation({
    meta: { silent: true },
    mutationFn: (values: Signup) =>
      http.post<AuthResponse>('/auth/register', {
        ...values,
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      }),
    onSuccess: (auth) => {
      signIn(auth)
      navigate('/', { replace: true })
    },
    onError: (error) => handleFormError(form, error),
  })

  if (me) return <Navigate to="/" replace />

  const slugTaken = slug.length >= 3 && availability.data?.available === false

  return (
    <AuthShell title="Create your workspace" subtitle="Set up OrbitCRM for your company in a minute. You'll be the administrator.">
      <Form<Signup>
        form={form}
        layout="vertical"
        requiredMark={false}
        onFinish={(values) => register.mutate(values)}
        onValuesChange={(changed: Partial<Signup>) => {
          // Suggest a workspace URL from the company name until the user types their own.
          if (changed.companyName !== undefined && !slugEdited) {
            form.setFieldValue('tenantSlug', toSlug(changed.companyName))
          }
        }}
      >
        <Form.Item name="companyName" label="Company name" rules={[{ required: true, min: 2, message: 'Enter your company name' }]}>
          <Input placeholder="Acme India" />
        </Form.Item>
        <Form.Item
          name="tenantSlug"
          label="Workspace name"
          extra={slugTaken ? undefined : 'You will use this to sign in. Lowercase letters, digits and hyphens.'}
          validateStatus={slugTaken ? 'error' : undefined}
          help={slugTaken ? 'This workspace name is taken or not allowed' : undefined}
          rules={[{ required: true, pattern: /^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$/, message: '3-40 lowercase letters, digits or hyphens' }]}
        >
          <Input placeholder="acme" onChange={() => setSlugEdited(true)} />
        </Form.Item>
        <Row gutter={12}>
          <Col span={12}>
            <Form.Item name="firstName" label="First name" rules={[{ required: true, message: 'Required' }]}>
              <Input />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="lastName" label="Last name" rules={[{ required: true, message: 'Required' }]}>
              <Input />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="email" label="Work email" rules={[{ required: true, type: 'email', message: 'Enter a valid email' }]}>
          <Input type="email" autoComplete="email" />
        </Form.Item>
        <Form.Item name="password" label="Password" extra={PASSWORD_HINT} rules={[{ required: true, min: 10, message: 'Password is too short' }]}>
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Button type="primary" htmlType="submit" size="large" block loading={register.isPending} disabled={slugTaken}>
          Create workspace
        </Button>
      </Form>
      <p style={{ marginTop: 24, textAlign: 'center' }}>
        Already have a workspace? <Link to="/login">Sign in</Link>
      </p>
    </AuthShell>
  )
}
