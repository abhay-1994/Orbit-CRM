import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Checkbox, Col, DatePicker, Divider, Form, Input, InputNumber, Modal, Radio, Row, Skeleton, Tag } from 'antd'
import type { Dayjs } from 'dayjs'
import { useNavigate } from 'react-router'
import { http } from '../../api/client'
import type { ConversionPreview, ConversionResult, ConversionSuggestion, RecordView } from '../../api/types'
import { useMe } from '../../auth/context'
import { RecordSelect } from '../../components/pickers'
import { toast } from '../../lib/feedback'
import { currencySymbol } from '../../lib/format'

interface ConvertForm {
  companyAction: 'CREATE' | 'EXISTING' | 'NONE'
  companyId?: string
  contactAction: 'CREATE' | 'EXISTING' | 'NONE'
  contactId?: string
  createDeal: boolean
  dealName?: string
  amount?: number
  expectedCloseDate?: Dayjs
}

/** Converts a lead into a company, a contact and optionally a deal, all at once. */
export function ConvertLeadModal({ lead, onClose }: { lead: RecordView; onClose: () => void }) {
  const me = useMe()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [form] = Form.useForm<ConvertForm>()
  const preview = useQuery({
    queryKey: ['conversion-preview', lead.id],
    queryFn: () => http.get<ConversionPreview>(`/leads/${lead.id}/conversion-preview`),
  })

  const convert = useMutation({
    mutationFn: (v: ConvertForm) =>
      http.post<ConversionResult>(`/leads/${lead.id}/convert`, {
        company: { action: v.companyAction, id: v.companyId },
        contact: { action: v.contactAction, id: v.contactId },
        deal: v.createDeal
          ? { action: 'CREATE', values: { name: v.dealName, amount: v.amount, expectedCloseDate: v.expectedCloseDate?.format('YYYY-MM-DD') } }
          : { action: 'NONE' },
      }),
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: ['records'] })
      void queryClient.invalidateQueries({ queryKey: ['record', 'leads', lead.id] })
      toast.success('Lead converted')
      const target = result.contact ?? result.company
      if (target) navigate(`/${target.module}/${target.id}`)
      onClose()
    },
  })

  const data = preview.data
  const companyAction = Form.useWatch('companyAction', form)
  const contactAction = Form.useWatch('contactAction', form)
  const createDeal = Form.useWatch('createDeal', form)

  return (
    <Modal open width={640} title={`Convert ${lead.title}`} okText="Convert lead" onOk={() => form.submit()} confirmLoading={convert.isPending} onCancel={onClose} destroyOnHidden>
      {!data ? (
        <Skeleton active />
      ) : (
        <Form<ConvertForm>
          form={form}
          layout="vertical"
          onFinish={(values) => convert.mutate(values)}
          initialValues={{
            companyAction: data.company.name ? 'CREATE' : 'NONE',
            contactAction: 'CREATE',
            createDeal: false,
            dealName: data.deal.name as string,
          }}
        >
          <Divider titlePlacement="start">Company</Divider>
          <Form.Item name="companyAction">
            <Radio.Group
              options={[
                { value: 'CREATE', label: data.company.name ? `Create "${data.company.name as string}"` : 'Create new', disabled: !data.company.name },
                { value: 'EXISTING', label: 'Link existing' },
                { value: 'NONE', label: "Don't add a company" },
              ]}
            />
          </Form.Item>
          <Matches label="Possible matches" items={data.matchingCompanies} onPick={(id) => form.setFieldsValue({ companyAction: 'EXISTING', companyId: id })} />
          {companyAction === 'EXISTING' && (
            <Form.Item name="companyId" rules={[{ required: true, message: 'Choose a company' }]}>
              <RecordSelect<string> module="companies" placeholder="Search companies" />
            </Form.Item>
          )}

          <Divider titlePlacement="start">Contact</Divider>
          <Form.Item name="contactAction">
            <Radio.Group
              options={[
                { value: 'CREATE', label: `Create "${lead.title}"` },
                { value: 'EXISTING', label: 'Link existing' },
                { value: 'NONE', label: "Don't add a contact" },
              ]}
            />
          </Form.Item>
          <Matches label="Contacts with the same email" items={data.matchingContacts} onPick={(id) => form.setFieldsValue({ contactAction: 'EXISTING', contactId: id })} />
          {contactAction === 'EXISTING' && (
            <Form.Item name="contactId" rules={[{ required: true, message: 'Choose a contact' }]}>
              <RecordSelect<string> module="contacts" placeholder="Search contacts" />
            </Form.Item>
          )}

          <Divider titlePlacement="start">Deal</Divider>
          <Form.Item name="createDeal" valuePropName="checked">
            <Checkbox>Create a deal</Checkbox>
          </Form.Item>
          {createDeal && (
            <Row gutter={12}>
              <Col span={24}>
                <Form.Item name="dealName" label="Deal name" rules={[{ required: true, message: 'Name the deal' }]}>
                  <Input />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="amount" label="Amount">
                  <InputNumber min={0} prefix={currencySymbol(me.tenant)} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="expectedCloseDate" label="Expected close date">
                  <DatePicker format="DD MMM YYYY" style={{ width: '100%' }} />
                </Form.Item>
              </Col>
            </Row>
          )}
          <p className="muted" style={{ marginBottom: 0 }}>
            The lead's notes, files and activities move to the new contact, and the lead becomes read-only.
          </p>
        </Form>
      )}
    </Modal>
  )
}

function Matches({ label, items, onPick }: { label: string; items: ConversionSuggestion[]; onPick: (id: string) => void }) {
  if (items.length === 0) return null
  return (
    <div style={{ margin: '-8px 0 12px' }}>
      <span className="muted">{label}: </span>
      {items.map((item) => (
        <Tag key={item.id} color="purple" style={{ cursor: 'pointer' }} onClick={() => onPick(item.id)}>
          {item.title} · {item.recordNumber}
        </Tag>
      ))}
    </div>
  )
}
