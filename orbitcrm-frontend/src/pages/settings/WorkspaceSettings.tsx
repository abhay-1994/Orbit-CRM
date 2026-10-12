import { useMutation, useQuery } from '@tanstack/react-query'
import { Button, Col, Form, Input, Row, Select, Skeleton } from 'antd'
import dayjs from 'dayjs'
import { http } from '../../api/client'
import type { TenantInfo } from '../../api/types'
import { useAuth, useMe } from '../../auth/context'
import { handleFormError, toast } from '../../lib/feedback'
import { useCan } from '../../lib/hooks'
import { SettingsSection } from './SettingsLayout'

const TIME_ZONES = Intl.supportedValuesOf('timeZone').map((zone) => ({ value: zone, label: zone }))
const CURRENCIES = Intl.supportedValuesOf('currency').map((code) => ({ value: code, label: code }))
const MONTHS = Array.from({ length: 12 }, (_, i) => ({ value: i + 1, label: dayjs().month(i).format('MMMM') }))

export function WorkspaceSettings() {
  const me = useMe()
  const can = useCan()
  const { updateMe } = useAuth()
  const [form] = Form.useForm<TenantInfo>()
  const tenant = useQuery({ queryKey: ['tenant'], queryFn: () => http.get<TenantInfo>('/tenant') })
  const editable = can('settings', 'UPDATE')

  const save = useMutation({
    meta: { silent: true },
    mutationFn: (values: Partial<TenantInfo>) => http.patch<TenantInfo>('/tenant', values),
    onSuccess: (updated) => {
      updateMe({ ...me, tenant: { ...me.tenant, ...updated } })
      toast.success('Workspace settings saved')
    },
    onError: (error) => handleFormError(form, error),
  })

  return (
    <SettingsSection title="Workspace" description="Your organization's name and regional settings, used for dates, amounts and fiscal periods.">
      {!tenant.data ? (
        <Skeleton active />
      ) : (
        <Form form={form} layout="vertical" initialValues={tenant.data} disabled={!editable} onFinish={(values) => save.mutate(values)} style={{ maxWidth: 680 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="name" label="Company name" rules={[{ required: true, min: 2 }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="Workspace name" extra="Used to sign in; cannot be changed.">
                <Input value={tenant.data.slug} disabled />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="timezone" label="Time zone">
                <Select showSearch={{ optionFilterProp: 'label' }} options={TIME_ZONES} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="currency" label="Currency">
                <Select showSearch={{ optionFilterProp: 'label' }} options={CURRENCIES} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="locale" label="Number and date format" extra="A locale such as en-IN or en-US.">
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="fiscalYearStartMonth" label="Fiscal year starts in" extra="Used by 'this quarter' and 'this year' on the dashboard.">
                <Select options={MONTHS} />
              </Form.Item>
            </Col>
          </Row>
          {editable && (
            <Button type="primary" htmlType="submit" loading={save.isPending}>
              Save settings
            </Button>
          )}
        </Form>
      )}
    </SettingsSection>
  )
}
