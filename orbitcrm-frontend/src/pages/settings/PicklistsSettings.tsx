import { ArrowDownOutlined, ArrowUpOutlined, PlusOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, ColorPicker, Form, Input, Radio, Segmented, Skeleton, Space, Switch, Table, Tag } from 'antd'
import { useState } from 'react'
import { http } from '../../api/client'
import type { PicklistResponse, PicklistValueView } from '../../api/types'
import { toast } from '../../lib/feedback'
import { useCan } from '../../lib/hooks'
import { SettingsSection } from './SettingsLayout'

/** Tenant-editable dropdowns such as lead source and industry. Values are deactivated, never deleted. */
export function PicklistsSettings() {
  const can = useCan()
  const queryClient = useQueryClient()
  const picklists = useQuery({ queryKey: ['picklists'], queryFn: () => http.get<PicklistResponse[]>('/picklists') })
  const [selectedKey, setSelectedKey] = useState<string>()
  const current = picklists.data?.find((p) => p.key === selectedKey) ?? picklists.data?.[0]
  const [form] = Form.useForm()

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['picklists'] })
    void queryClient.invalidateQueries({ queryKey: ['meta'] })
  }
  const update = useMutation({
    mutationFn: ({ id, ...changes }: { id: string; label?: string; color?: string; active?: boolean; isDefault?: boolean }) =>
      http.patch(`/picklists/${current!.key}/values/${id}`, changes),
    onSuccess: refresh,
  })
  const add = useMutation({
    mutationFn: (values: { label: string; color?: string }) => http.post(`/picklists/${current!.key}/values`, values),
    onSuccess: () => {
      form.resetFields()
      toast.success('Value added')
      refresh()
    },
  })
  const reorder = useMutation({
    mutationFn: (ids: string[]) => http.put(`/picklists/${current!.key}/order`, { valueIds: ids }),
    onSuccess: refresh,
  })

  const move = (values: PicklistValueView[], index: number, by: number) => {
    const ids = values.map((v) => v.id)
    ;[ids[index], ids[index + by]] = [ids[index + by], ids[index]]
    reorder.mutate(ids)
  }

  return (
    <SettingsSection
      title="Dropdown values"
      description="Rename, recolor, reorder or switch off values. Switched-off values stay on existing records but can't be chosen for new ones."
      allowed={can('settings', 'UPDATE')}
    >
      {!picklists.data || !current ? (
        <Skeleton active />
      ) : (
        <>
          <Segmented
            value={current.key}
            onChange={(key) => setSelectedKey(key as string)}
            options={picklists.data.map((p) => ({ value: p.key, label: p.label }))}
            className="scroll-x"
            style={{ marginBottom: 16 }}
          />
          <Table<PicklistValueView>
            rowKey="id"
            size="small"
            pagination={false}
            dataSource={current.values}
            columns={[
              {
                title: 'Order',
                width: 90,
                render: (_, __, index) => (
                  <Space size={0}>
                    <Button type="text" size="small" icon={<ArrowUpOutlined />} disabled={index === 0} onClick={() => move(current.values, index, -1)} />
                    <Button type="text" size="small" icon={<ArrowDownOutlined />} disabled={index === current.values.length - 1} onClick={() => move(current.values, index, 1)} />
                  </Space>
                ),
              },
              {
                title: 'Label',
                render: (_, v) => (
                  <Input
                    defaultValue={v.label}
                    variant="borderless"
                    onBlur={(e) => e.target.value.trim() && e.target.value !== v.label && update.mutate({ id: v.id, label: e.target.value.trim() })}
                    onPressEnter={(e) => e.currentTarget.blur()}
                  />
                ),
              },
              {
                title: 'Color',
                width: 80,
                render: (_, v) => <ColorPicker size="small" value={v.color} onChangeComplete={(color) => update.mutate({ id: v.id, color: color.toHexString() })} />,
              },
              { title: 'Stored as', render: (_, v) => <code className="muted">{v.key}</code> },
              {
                title: 'Default',
                width: 80,
                render: (_, v) => <Radio checked={v.isDefault} onChange={() => update.mutate({ id: v.id, isDefault: true })} />,
              },
              {
                title: 'Active',
                width: 120,
                render: (_, v) =>
                  v.system ? <Tag>System</Tag> : <Switch size="small" checked={v.active} onChange={(active) => update.mutate({ id: v.id, active })} />,
              },
            ]}
          />
          <Form form={form} layout="inline" style={{ marginTop: 16 }} onFinish={(values) => add.mutate({ label: values.label, color: values.color?.toHexString?.() })}>
            <Form.Item name="label" rules={[{ required: true, message: 'Enter a label' }]}>
              <Input placeholder="New value" style={{ width: 240 }} />
            </Form.Item>
            <Form.Item name="color">
              <ColorPicker />
            </Form.Item>
            <Button htmlType="submit" icon={<PlusOutlined />} loading={add.isPending}>
              Add value
            </Button>
          </Form>
        </>
      )}
    </SettingsSection>
  )
}
