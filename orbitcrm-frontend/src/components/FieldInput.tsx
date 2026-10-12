import { DatePicker, Form, Input, InputNumber, Select, Switch } from 'antd'
import dayjs from 'dayjs'
import { useEffect, useRef } from 'react'
import type { FieldMeta, FieldOption } from '../api/types'
import { useMe } from '../auth/context'
import { currencySymbol } from '../lib/format'
import { usePipelines } from '../lib/hooks'
import { RecordSelect, UserSelect } from './pickers'

interface FieldInputProps {
  /** Set by Form.Item so the label is linked to the input. */
  id?: string
  field: FieldMeta
  /** Display labels of the record being edited, for lookups that have not been searched yet. */
  labels?: Record<string, string>
  value?: unknown
  onChange?: (value: unknown) => void
}

/** Options a user may pick: active ones, plus inactive ones already selected on this record. */
function selectableOptions(options: FieldOption[] = [], value: unknown) {
  const selected = Array.isArray(value) ? value : [value]
  return options
    .filter((o) => o.active || selected.includes(o.key))
    .map((o) => ({ value: o.key, label: o.label }))
}

/**
 * Input for one record field, chosen from its metadata type. Values stay in API format
 * (ISO strings for dates), so forms need no conversion before saving.
 */
export function FieldInput({ id, field, labels, value, onChange }: FieldInputProps) {
  const me = useMe()
  const change = (next: unknown) => onChange?.(next)

  switch (field.type) {
    case 'TEXTAREA':
      return (
        <Input.TextArea
          id={id}
          value={(value as string) ?? ''}
          onChange={(e) => change(e.target.value)}
          autoSize={{ minRows: 3, maxRows: 8 }}
          maxLength={field.maxLength}
        />
      )
    case 'INTEGER':
    case 'DECIMAL':
    case 'CURRENCY':
    case 'PERCENT':
      return (
        <InputNumber
          id={id}
          value={value as number | null}
          onChange={change}
          min={field.min ?? (field.type === 'PERCENT' ? 0 : undefined)}
          max={field.type === 'PERCENT' ? 100 : field.max}
          precision={field.type === 'INTEGER' ? 0 : undefined}
          prefix={field.type === 'CURRENCY' ? currencySymbol(me.tenant) : undefined}
          suffix={field.type === 'PERCENT' ? '%' : undefined}
          style={{ width: '100%' }}
        />
      )
    case 'DATE':
      return (
        <DatePicker
          id={id}
          value={value ? dayjs(value as string) : null}
          onChange={(d) => change(d ? d.format('YYYY-MM-DD') : null)}
          format="DD MMM YYYY"
          style={{ width: '100%' }}
        />
      )
    case 'DATETIME':
      return (
        <DatePicker
          id={id}
          showTime={{ format: 'h:mm A', minuteStep: 5 }}
          value={value ? dayjs(value as string) : null}
          onChange={(d) => change(d ? d.toISOString() : null)}
          format="DD MMM YYYY, h:mm A"
          style={{ width: '100%' }}
        />
      )
    case 'BOOLEAN':
      return <Switch id={id} checked={value === true} onChange={change} />
    case 'PICKLIST':
    case 'MULTI_PICKLIST':
      return (
        <Select
          id={id}
          mode={field.type === 'MULTI_PICKLIST' ? 'multiple' : undefined}
          value={value}
          onChange={change}
          options={selectableOptions(field.options, value)}
          allowClear
        />
      )
    case 'TAGS':
      return <Select id={id} mode="tags" value={value} onChange={change} tokenSeparators={[',']} placeholder="Add tags" />
    case 'USER':
      return <UserSelect id={id} value={value as string} onChange={change} initialLabel={labels?.[field.key]} />
    case 'MULTI_USER':
      return <UserSelect id={id} multiple value={value as string[]} onChange={change} placeholder="Select users" />
    case 'LOOKUP':
      return <LookupInput id={id} field={field} value={value as string} onChange={change} initialLabel={labels?.[field.key]} />
    case 'REFERENCE':
      return <ReferenceInput id={id} field={field} value={value as string} onChange={change} />
    default:
      return (
        <Input
          id={id}
          value={(value as string) ?? ''}
          onChange={(e) => change(e.target.value)}
          maxLength={field.maxLength}
          type={field.type === 'EMAIL' ? 'email' : 'text'}
        />
      )
  }
}

/**
 * Clears the value when the field it depends on changes. Transitions from "no value yet" are ignored,
 * because Form.useWatch reports undefined on the first render of an edit form.
 */
function useResetOnChange(dependency: unknown, value: unknown, onChange: (v: unknown) => void) {
  const previous = useRef(dependency)
  useEffect(() => {
    if (previous.current !== undefined && previous.current !== dependency && value) onChange(null)
    previous.current = dependency
  }, [dependency, value, onChange])
}

/** Lookup to another record; a polymorphic lookup takes its target module from a sibling field. */
function LookupInput({ id, field, value, onChange, initialLabel }: { id?: string; field: FieldMeta; value?: string; onChange: (v: unknown) => void; initialLabel?: string }) {
  const relatedModule = Form.useWatch(field.lookupModuleField ?? '__none__') as string | undefined
  const module = field.lookupModule ?? relatedModule
  useResetOnChange(module, value, onChange)
  return <RecordSelect id={id} module={module} value={value} onChange={onChange} initialLabel={initialLabel} placeholder={module ? 'Search…' : 'Choose a type first'} />
}

/** Pipeline/stage style references; stages are limited to the pipeline chosen on the form. */
function ReferenceInput({ id, field, value, onChange }: { id?: string; field: FieldMeta; value?: string; onChange: (v: unknown) => void }) {
  const pipelineId = Form.useWatch('pipelineId') as string | undefined
  const pipelines = usePipelines()
  const isStage = field.referenceType === 'stage'
  useResetOnChange(isStage ? pipelineId : undefined, value, onChange)

  const options = isStage
    ? (pipelines.data?.find((p) => p.id === pipelineId) ?? pipelines.data?.find((p) => p.isDefault))?.stages
        .filter((s) => s.active || s.id === value)
        .map((s) => ({ value: s.id, label: `${s.name} (${s.probability}%)` }))
    : selectableOptions(field.options, value)

  return <Select id={id} value={value} onChange={onChange} options={options} allowClear placeholder={isStage ? 'First stage' : 'Default'} />
}
