import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Button, Col, Drawer, Form, Row, Skeleton, Space, Typography } from 'antd'
import type { FieldMeta, RecordValues, RecordView } from '../api/types'
import { http } from '../api/client'
import { useMe } from '../auth/context'
import { handleFormError, toast } from '../lib/feedback'
import { useModuleMeta } from '../lib/hooks'
import { MODULE_BY_KEY } from '../lib/modules'
import { FieldInput } from './FieldInput'

const GROUP_TITLES: Record<string, string> = {
  details: 'Details',
  address: 'Address',
  system: 'Assignment',
}

interface Props {
  module: string
  /** The record to edit; omit to create a new one. */
  record?: RecordView
  /** Prefilled values for a new record, e.g. the company of a related contact. */
  initialValues?: RecordValues
  /** Labels for prefilled lookups, so they show a name instead of an id. */
  initialLabels?: Record<string, string>
  onClose: () => void
  onSaved?: (record: RecordView) => void
}

const normalize = (v: unknown) => (v === undefined || v === '' ? null : v)
const same = (a: unknown, b: unknown) => JSON.stringify(normalize(a)) === JSON.stringify(normalize(b))

/** Create / edit form for any module, built from its field metadata. */
export function RecordFormDrawer({ module, record, initialValues, initialLabels, onClose, onSaved }: Props) {
  const me = useMe()
  const queryClient = useQueryClient()
  const [form] = Form.useForm()
  const meta = useModuleMeta(module)
  const info = MODULE_BY_KEY[module]

  const editable = (meta.data?.fields ?? []).filter((f) => !f.readOnly)
  const byGroup = new Map<string, FieldMeta[]>()
  editable.forEach((f) => byGroup.set(f.group, [...(byGroup.get(f.group) ?? []), f]))
  const groups = [...byGroup].sort(([a], [b]) => groupOrder(a) - groupOrder(b))

  const startValues: RecordValues = record
    ? Object.fromEntries(editable.map((f) => [f.key, record.values[f.key]]))
    : {
        ...Object.fromEntries(
          editable.flatMap((f) => {
            const preset = f.type === 'PICKLIST' ? f.options?.find((o) => o.isDefault && o.active)?.key : undefined
            return preset ? [[f.key, preset]] : []
          }),
        ),
        ownerId: me.id,
        ...initialValues,
      }
  const labels = record?.labels ?? { ownerId: me.fullName, ...initialLabels }

  const save = useMutation({
    meta: { silent: true },
    mutationFn: (values: RecordValues) => {
      if (!record) {
        return http.post<RecordView>(`/${module}`, Object.fromEntries(Object.entries(values).filter(([, v]) => v !== undefined)))
      }
      const changes = Object.fromEntries(
        Object.entries(values)
          .filter(([key, v]) => !same(v, record.values[key]))
          .map(([key, v]) => [key, normalize(v)]),
      )
      return Object.keys(changes).length === 0
        ? Promise.resolve(record)
        : http.patch<RecordView>(`/${module}/${record.id}`, { ...changes, version: record.version })
    },
    onSuccess: (saved) => {
      // Lists, the record itself and the views that summarise records (board, calendar, dashboard).
      for (const key of [['records', module], ['record', module, saved.id], ['timeline'], ['board'], ['calendar'], ['dashboard']]) {
        void queryClient.invalidateQueries({ queryKey: key })
      }
      toast.success(`${info?.singular ?? 'Record'} ${record ? 'updated' : 'created'}`)
      onSaved?.(saved)
      onClose()
    },
    onError: (error) => handleFormError(form, error),
  })

  return (
    <Drawer
      open
      size={680}
      onClose={onClose}
      destroyOnHidden
      title={record ? `Edit ${record.title}` : `New ${info?.singular.toLowerCase() ?? 'record'}`}
      footer={
        <Space style={{ float: 'right' }}>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="primary" loading={save.isPending} onClick={() => form.submit()}>
            {record ? 'Save changes' : 'Create'}
          </Button>
        </Space>
      }
    >
      {meta.isLoading ? (
        <Skeleton active paragraph={{ rows: 10 }} />
      ) : (
        <Form form={form} layout="vertical" initialValues={startValues} onFinish={(values) => save.mutate(values)}>
          {groups.map(([group, fields]) => (
            <div key={group}>
              <Typography.Title level={5} style={{ margin: '4px 0 12px' }}>
                {GROUP_TITLES[group] ?? group.charAt(0).toUpperCase() + group.slice(1)}
              </Typography.Title>
              <Row gutter={16}>
                {fields.map((field) => (
                  <Col key={field.key} span={field.type === 'TEXTAREA' ? 24 : 12}>
                    <Form.Item
                      name={field.key}
                      label={field.label}
                      tooltip={field.helpText}
                      // Pipeline and stage are chosen by the server when left empty.
                      rules={field.required && field.type !== 'REFERENCE' ? [{ required: true, message: `${field.label} is required` }] : []}
                    >
                      <FieldInput field={field} labels={labels} />
                    </Form.Item>
                  </Col>
                ))}
              </Row>
            </div>
          ))}
        </Form>
      )}
    </Drawer>
  )
}

function groupOrder(group: string): number {
  return group === 'details' ? 0 : group === 'system' ? 9 : group === 'address' ? 5 : 3
}
