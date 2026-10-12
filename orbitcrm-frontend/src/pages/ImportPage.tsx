import { CloudUploadOutlined, InboxOutlined } from '@ant-design/icons'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Progress, Radio, Result, Select, Space, Statistic, Steps, Table, Tag, Upload } from 'antd'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { download, http, upload } from '../api/client'
import type { ImportJob, ImportPreview } from '../api/types'
import { showError } from '../lib/feedback'
import { useCan, useModuleMeta } from '../lib/hooks'
import { MODULES, MODULE_BY_KEY } from '../lib/modules'

type Strategy = 'SKIP' | 'UPDATE' | 'CREATE'

/** CSV import wizard: upload, map columns, preview, run, review results. */
export function ImportPage() {
  const can = useCan()
  const [params] = useSearchParams()
  const importable = MODULES.filter((m) => can(m.key, 'IMPORT') && can(m.key, 'CREATE'))
  const [module, setModule] = useState(params.get('module') ?? importable[0]?.key)
  const [job, setJob] = useState<ImportJob | null>(null)
  const [mapping, setMapping] = useState<Record<string, string | null>>({})
  const [duplicateField, setDuplicateField] = useState<string | undefined>('email')
  const [strategy, setStrategy] = useState<Strategy>('SKIP')
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [started, setStarted] = useState(false)
  const meta = useModuleMeta(module)
  const info = module ? MODULE_BY_KEY[module] : undefined

  const uploadFile = useMutation({
    mutationFn: (file: File) => upload<ImportJob>(`/imports?module=${module}`, file),
    onSuccess: (uploaded) => {
      setJob(uploaded)
      setMapping(uploaded.mapping)
      setPreview(null)
    },
  })
  const configure = useMutation({
    mutationFn: () =>
      http.put<ImportPreview>(`/imports/${job!.id}/mapping`, {
        mapping,
        duplicateField: meta.data?.fields.some((f) => f.key === duplicateField) ? duplicateField : null,
        duplicateStrategy: strategy,
      }),
    onSuccess: setPreview,
  })
  const start = useMutation({
    mutationFn: () => http.post<ImportJob>(`/imports/${job!.id}/start`),
    onSuccess: () => setStarted(true),
  })
  const status = useQuery({
    queryKey: ['import', job?.id],
    queryFn: () => http.get<ImportJob>(`/imports/${job!.id}`),
    enabled: started && !!job,
    refetchInterval: (query) => (['QUEUED', 'RUNNING'].includes(query.state.data?.status ?? 'QUEUED') ? 1000 : false),
  })

  const reset = () => {
    setJob(null)
    setPreview(null)
    setStarted(false)
  }

  if (importable.length === 0) return <Result status="403" title="You don't have permission to import records" />

  const writable = (meta.data?.fields ?? []).filter((f) => !f.readOnly)
  const step = started ? 2 : job ? 1 : 0
  const result = status.data
  const finished = result && ['COMPLETED', 'FAILED'].includes(result.status)

  return (
    <div>
      <div className="page-head">
        <div className="page-icon">
          <CloudUploadOutlined />
        </div>
        <div>
          <h1>Import records</h1>
          <p>Bring in a spreadsheet saved as CSV. Up to 50,000 rows; comma, semicolon or tab separated.</p>
        </div>
      </div>
      <Card>
        <Steps current={step} style={{ marginBottom: 24 }} items={[{ title: 'Upload file' }, { title: 'Map columns' }, { title: 'Import' }]} />

        {step === 0 && (
          <>
            <Space style={{ marginBottom: 16 }}>
              <span>Import into</span>
              <Select value={module} onChange={setModule} style={{ width: 200 }} options={importable.map((m) => ({ value: m.key, label: m.label }))} />
            </Space>
            <Upload.Dragger
              accept=".csv"
              showUploadList={false}
              disabled={uploadFile.isPending}
              beforeUpload={(file) => {
                uploadFile.mutate(file)
                return false
              }}
            >
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">{uploadFile.isPending ? 'Reading file…' : 'Click or drop a CSV file here'}</p>
              <p className="ant-upload-hint">The first row must contain column names. Dropdown values may be labels, owners may be emails or names.</p>
            </Upload.Dragger>
          </>
        )}

        {step === 1 && job && (
          <>
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              title={`${job.fileName}: ${job.totalRows} rows, ${job.columns.length} columns`}
              description="We matched columns to fields where the names agree. Leave a column unmapped to skip it."
            />
            <Table
              size="small"
              rowKey="column"
              pagination={false}
              dataSource={job.columns.map((column) => ({ column }))}
              columns={[
                { title: 'Column in your file', dataIndex: 'column' },
                {
                  title: `${info?.singular ?? ''} field`,
                  render: (_, { column }) => (
                    <Select
                      style={{ width: 260 }}
                      allowClear
                      placeholder="Don't import"
                      value={mapping[column] ?? undefined}
                      onChange={(field) => setMapping({ ...mapping, [column]: field ?? null })}
                      showSearch={{ optionFilterProp: 'label' }}
                      options={writable.map((f) => ({ value: f.key, label: f.label }))}
                    />
                  ),
                },
              ]}
            />
            <Space wrap style={{ margin: '20px 0' }}>
              <span>When a record with the same</span>
              <Select
                style={{ width: 180 }}
                allowClear
                placeholder="(no check)"
                value={duplicateField}
                onChange={setDuplicateField}
                options={writable.map((f) => ({ value: f.key, label: f.label }))}
              />
              <span>exists:</span>
              <Radio.Group
                optionType="button"
                value={strategy}
                onChange={(e) => setStrategy(e.target.value)}
                options={[
                  { value: 'SKIP', label: 'Skip the row' },
                  { value: 'UPDATE', label: 'Update it' },
                  { value: 'CREATE', label: 'Create anyway' },
                ]}
              />
            </Space>
            {preview && (
              <Table
                size="small"
                rowKey="row"
                pagination={false}
                style={{ marginBottom: 16 }}
                dataSource={preview.rows}
                columns={[
                  { title: 'Row', dataIndex: 'row', width: 70 },
                  { title: 'Values', render: (_, row) => Object.values(row.values).slice(0, 4).map(String).join(' · ') },
                  {
                    title: 'Check',
                    render: (_, row) =>
                      row.errors.length ? <span style={{ color: '#cf1322' }}>{row.errors.join('; ')}</span> : <Tag color="green">Ready</Tag>,
                  },
                ]}
              />
            )}
            <Space>
              <Button onClick={reset}>Choose another file</Button>
              <Button onClick={() => configure.mutate()} loading={configure.isPending}>
                {preview ? 'Refresh preview' : 'Preview first rows'}
              </Button>
              <Button type="primary" disabled={!preview} loading={start.isPending} onClick={() => start.mutate()}>
                Start import of {job.totalRows} rows
              </Button>
            </Space>
          </>
        )}

        {step === 2 && (
          <>
            {!finished ? (
              <div style={{ textAlign: 'center', padding: 32 }}>
                <Progress type="circle" percent={result ? Math.round((result.processedRows / Math.max(1, result.totalRows)) * 100) : 0} />
                <p className="muted" style={{ marginTop: 16 }}>
                  Importing… you can leave this page; the import continues in the background.
                </p>
              </div>
            ) : (
              <Result
                status={result.status === 'COMPLETED' ? 'success' : 'error'}
                title={result.status === 'COMPLETED' ? 'Import finished' : 'Import failed'}
                extra={
                  <Space>
                    {result.errors.length > 0 && (
                      <Button onClick={() => download(`/imports/${result.id}/errors.csv`).catch(showError)}>Download failed rows</Button>
                    )}
                    <Button onClick={reset}>Import another file</Button>
                    <Link to={`/${result.module}`}>
                      <Button type="primary">Go to {MODULE_BY_KEY[result.module]?.label.toLowerCase()}</Button>
                    </Link>
                  </Space>
                }
              >
                <Space size={48} style={{ justifyContent: 'center', width: '100%' }}>
                  <Statistic title="Created" value={result.created} />
                  <Statistic title="Updated" value={result.updated} />
                  <Statistic title="Skipped (duplicates)" value={result.skipped} />
                  <Statistic title="Failed" value={result.failed} styles={{ content: { color: result.failed ? '#cf1322' : undefined } }} />
                </Space>
                {result.errors.length > 0 && (
                  <Table
                    size="small"
                    rowKey="row"
                    style={{ marginTop: 24 }}
                    pagination={{ pageSize: 10 }}
                    dataSource={result.errors}
                    columns={[
                      { title: 'Row', dataIndex: 'row', width: 80 },
                      { title: 'Problem', render: (_, error) => error.messages.join('; ') },
                    ]}
                  />
                )}
              </Result>
            )}
          </>
        )}
      </Card>
    </div>
  )
}
