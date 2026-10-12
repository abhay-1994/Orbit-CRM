import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { Button, DatePicker, Empty, Input, InputNumber, Select, Space } from 'antd'
import dayjs from 'dayjs'
import { useState } from 'react'
import type { Condition, FieldMeta } from '../api/types'
import { RecordSelect, UserSelect } from './pickers'

const OPERATOR_LABELS: Record<string, string> = {
  eq: 'is',
  neq: 'is not',
  contains: 'contains',
  not_contains: 'does not contain',
  starts_with: 'starts with',
  gt: 'is greater than',
  gte: 'is at least',
  lt: 'is less than',
  lte: 'is at most',
  between: 'is between',
  in: 'is any of',
  not_in: 'is none of',
  contains_all: 'has all of',
  is_empty: 'is empty',
  is_not_empty: 'is not empty',
  within_last_days: 'in the last … days',
  within_next_days: 'in the next … days',
}

const NO_VALUE = ['is_empty', 'is_not_empty']
const MULTI = ['in', 'not_in', 'contains_all']

const hasValue = (c: Condition) =>
  NO_VALUE.includes(c.op) || (c.value !== undefined && c.value !== null && c.value !== '' && !(Array.isArray(c.value) && c.value.length === 0))

/** Edits a list of conditions that are combined with AND, using the operators each field supports. */
export function FilterBuilder({ fields, value, onApply }: { fields: FieldMeta[]; value: Condition[]; onApply: (conditions: Condition[]) => void }) {
  const filterable = fields.filter((f) => f.filterable)
  const [draft, setDraft] = useState<Condition[]>(value.length ? value : [])
  const update = (index: number, next: Partial<Condition>) => setDraft(draft.map((c, i) => (i === index ? { ...c, ...next } : c)))

  return (
    <div style={{ width: 640, maxWidth: '80vw' }}>
      {draft.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No conditions yet" />}
      {draft.map((condition, index) => {
        const field = filterable.find((f) => f.key === condition.field)
        return (
          <Space.Compact key={index} block style={{ marginBottom: 8 }}>
            <Select
              style={{ width: 180 }}
              value={condition.field}
              showSearch={{ optionFilterProp: 'label' }}
              options={filterable.map((f) => ({ value: f.key, label: f.label }))}
              onChange={(key) => {
                const next = filterable.find((f) => f.key === key)
                update(index, { field: key, op: next?.operators.includes('eq') ? 'eq' : next?.operators[0], value: undefined })
              }}
            />
            <Select
              style={{ width: 170 }}
              value={condition.op}
              options={(field?.operators ?? []).map((op) => ({ value: op, label: OPERATOR_LABELS[op] ?? op }))}
              onChange={(op) => update(index, { op, value: undefined })}
            />
            <div style={{ flex: 1 }}>
              {field && <ValueInput field={field} op={condition.op} value={condition.value} onChange={(v) => update(index, { value: v })} />}
            </div>
            <Button icon={<DeleteOutlined />} onClick={() => setDraft(draft.filter((_, i) => i !== index))} aria-label="Remove condition" />
          </Space.Compact>
        )
      })}
      <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
        <Button
          icon={<PlusOutlined />}
          onClick={() => {
            const first = filterable[0]
            setDraft([...draft, { field: first.key, op: first.operators.includes('eq') ? 'eq' : first.operators[0] }])
          }}
        >
          Add condition
        </Button>
        <span style={{ flex: 1 }} />
        <Button
          onClick={() => {
            setDraft([])
            onApply([])
          }}
        >
          Clear
        </Button>
        <Button type="primary" onClick={() => onApply(draft.filter(hasValue))}>
          Apply
        </Button>
      </div>
    </div>
  )
}

function ValueInput({ field, op, value, onChange }: { field: FieldMeta; op: string; value: unknown; onChange: (v: unknown) => void }) {
  if (NO_VALUE.includes(op)) return <Input disabled placeholder="No value needed" />
  if (op === 'within_last_days' || op === 'within_next_days') {
    return <InputNumber min={0} max={3650} value={value as number} onChange={onChange} suffix="days" style={{ width: '100%' }} />
  }
  const temporal = field.type === 'DATE' || field.type === 'DATETIME'
  if (op === 'between') {
    if (temporal) {
      const range = (value as string[] | undefined) ?? []
      return (
        <DatePicker.RangePicker
          style={{ width: '100%' }}
          value={range.length === 2 ? [dayjs(range[0]), dayjs(range[1])] : null}
          onChange={(dates) => onChange(dates?.[0] && dates[1] ? [dates[0].format('YYYY-MM-DD'), dates[1].format('YYYY-MM-DD')] : undefined)}
        />
      )
    }
    const [from, to] = (value as number[] | undefined) ?? []
    return (
      <Space.Compact block>
        <InputNumber placeholder="From" value={from} onChange={(v) => onChange([v, to])} style={{ width: '50%' }} />
        <InputNumber placeholder="To" value={to} onChange={(v) => onChange([from, v])} style={{ width: '50%' }} />
      </Space.Compact>
    )
  }

  const multiple = MULTI.includes(op) || (['contains', 'not_contains'].includes(op) && ['MULTI_PICKLIST', 'TAGS', 'MULTI_USER'].includes(field.type))
  switch (field.type) {
    case 'PICKLIST':
    case 'MULTI_PICKLIST':
    case 'REFERENCE':
      return (
        <Select
          mode={multiple ? 'multiple' : undefined}
          value={value}
          onChange={onChange}
          style={{ width: '100%' }}
          options={(field.options ?? []).map((o) => ({ value: o.key, label: o.group ? `${o.label} · ${o.group}` : o.label }))}
        />
      )
    case 'USER':
    case 'MULTI_USER':
      return <UserSelect withMe multiple={multiple} value={value as string} onChange={onChange} />
    case 'LOOKUP':
      return <RecordSelect module={field.lookupModule} multiple={multiple} value={value as string} onChange={onChange} />
    case 'TAGS':
      return <Select mode="tags" value={value} onChange={onChange} style={{ width: '100%' }} />
    case 'BOOLEAN':
      return <Select value={value} onChange={onChange} style={{ width: '100%' }} options={[{ value: true, label: 'Yes' }, { value: false, label: 'No' }]} />
    case 'DATE':
    case 'DATETIME':
      return (
        <DatePicker
          style={{ width: '100%' }}
          value={value ? dayjs(value as string) : null}
          onChange={(d) => onChange(d ? d.format('YYYY-MM-DD') : undefined)}
        />
      )
    case 'INTEGER':
    case 'DECIMAL':
    case 'CURRENCY':
    case 'PERCENT':
      return multiple ? (
        <Select mode="tags" value={value} onChange={onChange} style={{ width: '100%' }} />
      ) : (
        <InputNumber value={value as number} onChange={onChange} style={{ width: '100%' }} />
      )
    default:
      return multiple ? (
        <Select mode="tags" value={value} onChange={onChange} style={{ width: '100%' }} placeholder="Type and press Enter" />
      ) : (
        <Input value={(value as string) ?? ''} onChange={(e) => onChange(e.target.value)} />
      )
  }
}
