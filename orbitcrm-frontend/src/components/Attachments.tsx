import { DeleteOutlined, DownloadOutlined, InboxOutlined, PaperClipOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Empty, Popconfirm, Upload } from 'antd'
import { download, http, upload } from '../api/client'
import type { AttachmentView } from '../api/types'
import { showError, toast } from '../lib/feedback'
import { fileSize, fromNow } from '../lib/format'

/** Files attached to a record. The server checks file types (PDF, images, Office, CSV, text; max 25 MB). */
export function Attachments({ module, recordId }: { module: string; recordId: string }) {
  const queryClient = useQueryClient()
  const files = useQuery({
    queryKey: ['attachments', module, recordId],
    queryFn: () => http.get<AttachmentView[]>(`/${module}/${recordId}/attachments`),
  })
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['attachments', module, recordId] })
    void queryClient.invalidateQueries({ queryKey: ['timeline', module, recordId] })
  }
  const add = useMutation({
    mutationFn: (file: File) => upload(`/${module}/${recordId}/attachments`, file),
    onSuccess: () => {
      toast.success('File uploaded')
      refresh()
    },
  })
  const remove = useMutation({ mutationFn: (id: string) => http.delete(`/attachments/${id}`), onSuccess: refresh })

  return (
    <div>
      <Upload.Dragger
        showUploadList={false}
        multiple
        beforeUpload={(file) => {
          add.mutate(file)
          return false
        }}
        disabled={add.isPending}
      >
        <p className="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p className="ant-upload-text">{add.isPending ? 'Uploading…' : 'Click or drop files here'}</p>
        <p className="ant-upload-hint">PDF, images, Office documents, CSV or text, up to 25 MB</p>
      </Upload.Dragger>

      <div style={{ marginTop: 16 }}>
        {files.data?.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No files yet" />}
        {files.data?.map((file) => (
          <div key={file.id} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '10px 0', borderTop: '1px solid #f0f1f6' }}>
            <PaperClipOutlined style={{ fontSize: 18, color: '#bd38fa' }} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontWeight: 500 }}>{file.fileName}</div>
              <div className="muted" style={{ fontSize: 12 }}>
                {fileSize(file.sizeBytes)} · {file.uploadedByName} · {fromNow(file.createdAt)}
              </div>
            </div>
            <Button type="text" icon={<DownloadOutlined />} onClick={() => download(`/attachments/${file.id}/download`).catch(showError)} aria-label="Download" />
            <Popconfirm title="Delete this file?" onConfirm={() => remove.mutate(file.id)}>
              <Button type="text" danger icon={<DeleteOutlined />} aria-label="Delete" />
            </Popconfirm>
          </div>
        ))}
      </div>
    </div>
  )
}
