import {
  AppstoreOutlined,
  DeleteOutlined,
  DownloadOutlined,
  FilterOutlined,
  PlusOutlined,
  StarOutlined,
  TeamOutlined,
  UnorderedListOutlined,
  UploadOutlined,
  UserOutlined,
  UserSwitchOutlined,
} from '@ant-design/icons'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Badge, Button, Card, Form, Input, Modal, Popconfirm, Popover, Radio, Skeleton, Space, Table, type TableColumnsType } from 'antd'
import type { SortOrder, SorterResult } from 'antd/es/table/interface'
import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { download, http, qs } from '../../api/client'
import type { BulkResult, Condition, PageResponse, RecordView, SortSpec, ViewResponse } from '../../api/types'
import { useMe } from '../../auth/context'
import { FieldValue } from '../../components/FieldValue'
import { FilterBuilder } from '../../components/FilterBuilder'
import { UserSelect } from '../../components/pickers'
import { RecordFormDrawer } from '../../components/RecordFormDrawer'
import { toast } from '../../lib/feedback'
import { fromNow, plural } from '../../lib/format'
import { useCan, useModuleMeta } from '../../lib/hooks'
import { MODULE_BY_KEY } from '../../lib/modules'

/** A saved view's filter AND the user's extra conditions. */
function combine(viewFilter: unknown, conditions: Condition[]): unknown {
  const parts = [viewFilter, ...conditions].filter(Boolean)
  return parts.length === 0 ? undefined : parts.length === 1 ? parts[0] : { and: parts }
}

/** List page of any record module: saved views, search, filters, sorting, bulk actions, import/export. */
export function RecordListPage({ module }: { module: string }) {
  const me = useMe()
  const can = useCan()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [params, setParams] = useSearchParams()
  const meta = useModuleMeta(module)
  const info = MODULE_BY_KEY[module]

  // undefined = the user hasn't picked a view yet, so the ?view= link (from dashboard favourites) decides.
  const [chosenViewId, setChosenViewId] = useState<string | null>()
  const [q, setQ] = useState('')
  const [conditions, setConditions] = useState<Condition[]>([])
  const [sort, setSort] = useState<SortSpec | null>(null)
  const [paging, setPaging] = useState({ page: 0, size: 25 })
  const [selected, setSelected] = useState<string[]>([])
  const [filterOpen, setFilterOpen] = useState(false)
  const [creating, setCreating] = useState(false)
  const [savingView, setSavingView] = useState(false)

  const views = useQuery({
    queryKey: ['views', module],
    queryFn: () => http.get<ViewResponse[]>(`/views${qs({ module })}`),
  })

  // Favourite links open a saved view by name, e.g. /deals?view=My Open Deals
  const viewName = params.get('view')
  const viewId = chosenViewId !== undefined ? chosenViewId : (views.data?.find((v) => v.name === viewName)?.id ?? null)
  const view = views.data?.find((v) => v.id === viewId)
  const filter = combine(view?.filter, conditions)
  const request = { filter, q: q || undefined, sort: sort ? [sort] : view?.sort, page: paging.page, size: paging.size }
  const records = useQuery({
    queryKey: ['records', module, request],
    queryFn: () => http.post<PageResponse<RecordView>>(`/${module}/search`, request),
    placeholderData: keepPreviousData,
  })

  const refresh = () => {
    setSelected([])
    void queryClient.invalidateQueries({ queryKey: ['records', module] })
  }
  const reportBulk = (result: BulkResult, verb: string) => {
    toast.success(`${result.succeeded} of ${result.requested} records ${verb}`)
    if (result.failures.length) toast.error(`${result.failures.length} failed: ${result.failures[0].message}`)
    refresh()
  }
  const assign = useMutation({
    mutationFn: (ownerId: string) => http.post<BulkResult>(`/${module}/bulk-update`, { ids: selected, values: { ownerId } }),
    onSuccess: (result) => reportBulk(result, 'reassigned'),
  })
  const remove = useMutation({
    mutationFn: () => http.post<BulkResult>(`/${module}/bulk-delete`, { ids: selected }),
    onSuccess: (result) => reportBulk(result, 'deleted'),
  })
  const exportCsv = useMutation({
    mutationFn: () => download(`/${module}/export`, { filter, q: q || undefined, sort: request.sort }),
  })
  const deleteView = useMutation({
    mutationFn: (id: string) => http.delete(`/views/${id}`),
    onSuccess: (_, id) => {
      if (id === viewId) setChosenViewId(null)
      void queryClient.invalidateQueries({ queryKey: ['views', module] })
    },
  })

  const selectView = (id: string | null) => {
    setChosenViewId(id)
    if (viewName) setParams({}, { replace: true })
    setConditions([])
    setSort(null)
    setPaging((p) => ({ ...p, page: 0 }))
    setSelected([])
  }

  if (meta.isLoading || !meta.data) return <Skeleton active paragraph={{ rows: 12 }} />

  const fields = meta.data.fields
  const titleKey = meta.data.titleFields[0]
  const sortOrder = (key: string): SortOrder => (sort?.field === key ? (sort.direction === 'desc' ? 'descend' : 'ascend') : null)
  const columns: TableColumnsType<RecordView> = [
    {
      title: 'Name',
      key: titleKey,
      sorter: true,
      sortOrder: sortOrder(titleKey),
      render: (_, record) => (
        <Link to={`/${module}/${record.id}`}>
          <strong>{record.title}</strong>
          <div className="muted" style={{ fontSize: 12 }}>
            {record.recordNumber}
          </div>
        </Link>
      ),
    },
    ...fields
      .filter((f) => f.listDefault && !meta.data.titleFields.includes(f.key) && f.key !== 'ownerId')
      .map((field) => ({
        title: field.label,
        key: field.key,
        sorter: field.sortable,
        sortOrder: sortOrder(field.key),
        render: (_: unknown, record: RecordView) => <FieldValue field={field} record={record} />,
      })),
    { title: 'Owner', key: 'ownerId', render: (_, record) => record.labels.ownerId ?? '—' },
    {
      title: 'Created',
      key: 'createdAt',
      sorter: true,
      sortOrder: sortOrder('createdAt'),
      render: (_, record) => <span className="muted">{fromNow(record.values.createdAt)}</span>,
    },
  ]

  const canDeleteView = (v: ViewResponse) => !v.system && (v.ownerUserId === me.id || can('settings', 'UPDATE'))

  return (
    <div className="with-side">
      <aside className="side-panel">
        <button type="button" className={`side-link${viewId ? '' : ' active'}`} onClick={() => selectView(null)}>
          <UnorderedListOutlined />
          <span className="grow">All {info.label.toLowerCase()}</span>
        </button>
        <h4>Saved views</h4>
        {views.data?.length === 0 && (
          <p className="muted" style={{ margin: '4px 12px 8px', fontSize: 13 }}>
            Filter the list and choose “Save as view” to keep it here.
          </p>
        )}
        {views.data?.map((v) => (
          <button type="button" key={v.id} className={`side-link${viewId === v.id ? ' active' : ''}`} onClick={() => selectView(v.id)}>
            {v.system ? <StarOutlined /> : v.visibility === 'SHARED' ? <TeamOutlined /> : <UserOutlined />}
            <span className="grow">{v.name}</span>
            {canDeleteView(v) && (
              <Popconfirm title="Delete this view?" onConfirm={() => deleteView.mutate(v.id)}>
                <DeleteOutlined className="muted" onClick={(e) => e.stopPropagation()} />
              </Popconfirm>
            )}
          </button>
        ))}
      </aside>

      <div>
        <div className="page-head">
          <div className="page-icon">{info.icon}</div>
          <div>
            <h1>{view?.name ?? `All ${info.label.toLowerCase()}`}</h1>
            <p>
              {records.data?.totalElements ?? 0} {(records.data?.totalElements === 1 ? info.singular : info.label).toLowerCase()}
            </p>
          </div>
          <div className="actions">
            {module === 'deals' && (
              <Link to="/deals/board">
                <Button icon={<AppstoreOutlined />}>Board</Button>
              </Link>
            )}
            {can(module, 'IMPORT') && (
              <Button icon={<UploadOutlined />} onClick={() => navigate(`/import${qs({ module })}`)}>
                Import
              </Button>
            )}
            {can(module, 'EXPORT') && (
              <Button icon={<DownloadOutlined />} loading={exportCsv.isPending} onClick={() => exportCsv.mutate()}>
                Export
              </Button>
            )}
            {can(module, 'CREATE') && (
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>
                New {info.singular.toLowerCase()}
              </Button>
            )}
          </div>
        </div>

        <Card styles={{ body: { padding: 16 } }}>
          <div style={{ display: 'flex', gap: 8, marginBottom: 16, flexWrap: 'wrap', alignItems: 'center' }}>
            <Input.Search
              placeholder={`Search ${info.label.toLowerCase()}`}
              allowClear
              style={{ width: 300 }}
              onSearch={(value) => {
                setQ(value.trim())
                setPaging((p) => ({ ...p, page: 0 }))
              }}
            />
            <Popover
              open={filterOpen}
              onOpenChange={setFilterOpen}
              trigger="click"
              placement="bottomLeft"
              content={
                <FilterBuilder
                  key={JSON.stringify(conditions)}
                  fields={fields}
                  value={conditions}
                  onApply={(next) => {
                    setConditions(next)
                    setPaging((p) => ({ ...p, page: 0 }))
                    setFilterOpen(false)
                  }}
                />
              }
            >
              <Badge count={conditions.length} size="small">
                <Button icon={<FilterOutlined />}>Filters</Button>
              </Badge>
            </Popover>
            {conditions.length > 0 && <Button onClick={() => setSavingView(true)}>Save as view</Button>}
            {selected.length > 0 && (
              <Space style={{ marginLeft: 'auto' }}>
                <strong>{selected.length} selected</strong>
                {can(module, 'UPDATE') && (
                  <Popover
                    trigger="click"
                    content={<UserSelect<string> placeholder="Assign to…" style={{ width: 240 }} onChange={(id) => id && assign.mutate(id)} />}
                  >
                    <Button icon={<UserSwitchOutlined />} loading={assign.isPending}>
                      Assign owner
                    </Button>
                  </Popover>
                )}
                {can(module, 'DELETE') && (
                  <Popconfirm title={`Delete ${selected.length} records?`} onConfirm={() => remove.mutate()}>
                    <Button danger icon={<DeleteOutlined />} loading={remove.isPending}>
                      Delete
                    </Button>
                  </Popconfirm>
                )}
                <Button type="link" onClick={() => setSelected([])}>
                  Clear
                </Button>
              </Space>
            )}
          </div>

          <Table<RecordView>
            rowKey="id"
            columns={columns}
            dataSource={records.data?.content}
            loading={records.isFetching}
            scroll={{ x: 'max-content' }}
            rowSelection={
              can(module, 'UPDATE') || can(module, 'DELETE')
                ? { selectedRowKeys: selected, onChange: (keys) => setSelected(keys as string[]) }
                : undefined
            }
            pagination={{
              current: paging.page + 1,
              pageSize: paging.size,
              total: records.data?.totalElements,
              showSizeChanger: true,
              pageSizeOptions: [10, 25, 50, 100],
              showTotal: (total) => plural(total, 'record'),
            }}
            onChange={(pagination, _filters, sorter) => {
              const s = (Array.isArray(sorter) ? sorter[0] : sorter) as SorterResult<RecordView> | undefined
              setSort(s?.order ? { field: String(s.columnKey), direction: s.order === 'descend' ? 'desc' : 'asc' } : null)
              setPaging({ page: (pagination.current ?? 1) - 1, size: pagination.pageSize ?? 25 })
            }}
          />
        </Card>
      </div>

      {creating && <RecordFormDrawer module={module} onClose={() => setCreating(false)} onSaved={(r) => navigate(`/${module}/${r.id}`)} />}
      {savingView && (
        <SaveViewModal
          module={module}
          filter={filter}
          sort={request.sort}
          canShare={can('settings', 'UPDATE')}
          onClose={() => setSavingView(false)}
          onSaved={(saved) => {
            setSavingView(false)
            selectView(saved.id)
          }}
        />
      )}
    </div>
  )
}

function SaveViewModal({
  module,
  filter,
  sort,
  canShare,
  onClose,
  onSaved,
}: {
  module: string
  filter: unknown
  sort?: SortSpec[]
  canShare: boolean
  onClose: () => void
  onSaved: (view: ViewResponse) => void
}) {
  const queryClient = useQueryClient()
  const [form] = Form.useForm()
  const save = useMutation({
    mutationFn: (values: { name: string; visibility: string }) => http.post<ViewResponse>('/views', { module, ...values, filter, sort }),
    onSuccess: (view) => {
      void queryClient.invalidateQueries({ queryKey: ['views', module] })
      toast.success('View saved')
      onSaved(view)
    },
  })
  return (
    <Modal open title="Save as view" okText="Save" onOk={() => form.submit()} confirmLoading={save.isPending} onCancel={onClose} destroyOnHidden>
      <Form form={form} layout="vertical" initialValues={{ visibility: 'PRIVATE' }} onFinish={(values) => save.mutate(values)}>
        <Form.Item name="name" label="Name" rules={[{ required: true, max: 120, message: 'Give the view a name' }]}>
          <Input placeholder="e.g. Hot leads in Bengaluru" autoFocus />
        </Form.Item>
        {canShare && (
          <Form.Item name="visibility" label="Who can see it">
            <Radio.Group
              optionType="button"
              options={[
                { value: 'PRIVATE', label: 'Only me' },
                { value: 'SHARED', label: 'Everyone' },
              ]}
            />
          </Form.Item>
        )}
      </Form>
    </Modal>
  )
}
