import { ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Button, Card, Col, Form, Input, InputNumber, Modal, Popconfirm, Row, Select, Space, Switch, Tag } from 'antd'
import { useState } from 'react'
import { http } from '../../api/client'
import type { PipelineView, StageView } from '../../api/types'
import { handleFormError, toast } from '../../lib/feedback'
import { useCan, usePipelines } from '../../lib/hooks'
import { SettingsSection } from './SettingsLayout'

const TYPE_COLORS = { OPEN: 'blue', WON: 'cyan', LOST: 'red' } as const

export function PipelinesSettings() {
  const can = useCan()
  const queryClient = useQueryClient()
  const pipelines = usePipelines()
  const [editing, setEditing] = useState<PipelineView | 'new' | null>(null)
  const refresh = () => {
    for (const key of [['pipelines'], ['meta'], ['board']]) void queryClient.invalidateQueries({ queryKey: key })
  }
  const remove = useMutation({
    mutationFn: (id: string) => http.delete(`/pipelines/${id}`),
    onSuccess: () => {
      toast.success('Pipeline deleted')
      refresh()
    },
  })

  return (
    <SettingsSection
      title="Pipelines"
      description="The stages your deals move through. Each stage has a default win probability; won and lost stages close the deal."
      allowed={can('settings', 'UPDATE')}
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing('new')}>
          New pipeline
        </Button>
      }
    >
      <Row gutter={[16, 16]}>
        {pipelines.data?.map((pipeline) => (
          <Col key={pipeline.id} xs={24} xxl={12}>
            <Card
              size="small"
              title={
                <Space>
                  {pipeline.name}
                  {pipeline.isDefault && <Tag color="purple">Default</Tag>}
                  {!pipeline.active && <Tag>Inactive</Tag>}
                </Space>
              }
              extra={
                <Space>
                  <Button size="small" onClick={() => setEditing(pipeline)}>
                    Edit
                  </Button>
                  {!pipeline.isDefault && (
                    <Popconfirm title={`Delete ${pipeline.name}?`} description="Only pipelines without deals can be deleted." onConfirm={() => remove.mutate(pipeline.id)}>
                      <Button size="small" danger>
                        Delete
                      </Button>
                    </Popconfirm>
                  )}
                </Space>
              }
            >
              <Space wrap>
                {pipeline.stages.map((stage) => (
                  <Tag key={stage.id} color={stage.active ? TYPE_COLORS[stage.type] : undefined} style={{ opacity: stage.active ? 1 : 0.5 }}>
                    {stage.name} · {stage.probability}%
                  </Tag>
                ))}
              </Space>
            </Card>
          </Col>
        ))}
      </Row>
      {editing && (
        <PipelineModal
          pipeline={editing === 'new' ? undefined : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            refresh()
          }}
        />
      )}
    </SettingsSection>
  )
}

type StageForm = Pick<StageView, 'name' | 'probability' | 'type' | 'active'> & { id?: string }

const NEW_PIPELINE_STAGES: StageForm[] = [
  { name: 'New', probability: 10, type: 'OPEN', active: true },
  { name: 'Proposal', probability: 50, type: 'OPEN', active: true },
  { name: 'Won', probability: 100, type: 'WON', active: true },
  { name: 'Lost', probability: 0, type: 'LOST', active: true },
]

function PipelineModal({ pipeline, onClose, onSaved }: { pipeline?: PipelineView; onClose: () => void; onSaved: () => void }) {
  const [form] = Form.useForm()
  const save = useMutation({
    meta: { silent: true },
    mutationFn: (values: Record<string, unknown>) => (pipeline ? http.put(`/pipelines/${pipeline.id}`, values) : http.post('/pipelines', values)),
    onSuccess: () => {
      toast.success(pipeline ? 'Pipeline saved' : 'Pipeline created')
      onSaved()
    },
    onError: (error) => handleFormError(form, error),
  })

  return (
    <Modal open width={760} title={pipeline ? `Edit ${pipeline.name}` : 'New pipeline'} onOk={() => form.submit()} confirmLoading={save.isPending} onCancel={onClose} destroyOnHidden>
      <Form
        form={form}
        layout="vertical"
        initialValues={pipeline ?? { name: '', isDefault: false, active: true, stages: NEW_PIPELINE_STAGES }}
        onFinish={(values) => save.mutate(values)}
      >
        <Space align="start" wrap>
          <Form.Item name="name" label="Name" rules={[{ required: true, max: 120 }]}>
            <Input style={{ width: 300 }} />
          </Form.Item>
          <Form.Item name="isDefault" label="Default for new deals" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Space>
        <p className="muted">Stages, in order. Removing a stage that has deals is not possible; switch it off instead.</p>
        <Form.List name="stages">
          {(fields, { add, remove, move }) => (
            <>
              {fields.map((field, index) => (
                <Space key={field.key} align="baseline" style={{ display: 'flex', marginBottom: 4 }}>
                  <Form.Item name={[field.name, 'name']} rules={[{ required: true, message: 'Name' }]}>
                    <Input placeholder="Stage name" style={{ width: 220 }} />
                  </Form.Item>
                  <Form.Item name={[field.name, 'probability']} rules={[{ required: true, message: '%' }]}>
                    <InputNumber min={0} max={100} suffix="%" style={{ width: 100 }} />
                  </Form.Item>
                  <Form.Item name={[field.name, 'type']}>
                    <Select
                      style={{ width: 110 }}
                      options={[
                        { value: 'OPEN', label: 'Open' },
                        { value: 'WON', label: 'Won' },
                        { value: 'LOST', label: 'Lost' },
                      ]}
                    />
                  </Form.Item>
                  <Form.Item name={[field.name, 'active']} valuePropName="checked">
                    <Switch checkedChildren="On" unCheckedChildren="Off" />
                  </Form.Item>
                  <Button type="text" icon={<ArrowUpOutlined />} disabled={index === 0} onClick={() => move(index, index - 1)} />
                  <Button type="text" icon={<ArrowDownOutlined />} disabled={index === fields.length - 1} onClick={() => move(index, index + 1)} />
                  <Button type="text" danger icon={<DeleteOutlined />} onClick={() => remove(field.name)} />
                </Space>
              ))}
              <Button icon={<PlusOutlined />} onClick={() => add({ name: '', probability: 50, type: 'OPEN', active: true })}>
                Add stage
              </Button>
            </>
          )}
        </Form.List>
      </Form>
    </Modal>
  )
}
