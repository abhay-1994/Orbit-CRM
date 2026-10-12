import { AppstoreOutlined, PlusOutlined, UnorderedListOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Avatar, Button, Empty, Select, Skeleton, Tooltip, Typography } from 'antd'
import { useState } from 'react'
import { Link } from 'react-router'
import { http, qs } from '../api/client'
import type { PipelineBoard, RecordValues, RecordView } from '../api/types'
import { useMe } from '../auth/context'
import { RecordFormDrawer } from '../components/RecordFormDrawer'
import { toast } from '../lib/feedback'
import { formatDate, formatMoney, initials } from '../lib/format'
import { useCan, usePipelines } from '../lib/hooks'
import { brand } from '../theme'

const STAGE_COLORS = [brand.navy, '#3b2aa8', '#6a2fd0', brand.purple, '#7c6cf0', brand.cyan]

/** Kanban view of a pipeline. Drag a card onto another column to move the deal to that stage. */
export function DealBoardPage() {
  const me = useMe()
  const can = useCan()
  const queryClient = useQueryClient()
  const pipelines = usePipelines()
  const [pipelineId, setPipelineId] = useState<string>()
  const [dragging, setDragging] = useState<string | null>(null)
  const [dropTarget, setDropTarget] = useState<string | null>(null)
  const [creating, setCreating] = useState<RecordValues | null>(null)

  const board = useQuery({
    queryKey: ['board', pipelineId],
    queryFn: () => http.get<PipelineBoard>(`/deals/board${qs({ pipelineId, limitPerStage: 50 })}`),
  })
  const move = useMutation({
    mutationFn: ({ deal, stageId }: { deal: string; stageId: string }) => http.patch<RecordView>(`/deals/${deal}`, { stageId }),
    onSuccess: (deal) => {
      toast.success(`${deal.title} moved to ${deal.labels.stageId}`)
      void queryClient.invalidateQueries({ queryKey: ['board'] })
      void queryClient.invalidateQueries({ queryKey: ['records', 'deals'] })
    },
  })

  const money = (v: number) => formatMoney(v, me.tenant, true)
  const canMove = can('deals', 'UPDATE')

  return (
    <div>
      <div className="page-head">
        <div className="page-icon">
          <AppstoreOutlined />
        </div>
        <div>
          <h1>Pipeline board</h1>
          <p>Drag deals between stages. Weighted value = amount × stage probability.</p>
        </div>
        <div className="actions">
          <Select
            style={{ width: 220 }}
            value={pipelineId ?? board.data?.pipelineId}
            onChange={setPipelineId}
            options={pipelines.data?.filter((p) => p.active).map((p) => ({ value: p.id, label: p.name }))}
          />
          <Link to="/deals">
            <Button icon={<UnorderedListOutlined />}>List</Button>
          </Link>
          {can('deals', 'CREATE') && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating({ pipelineId: board.data?.pipelineId })}>
              New deal
            </Button>
          )}
        </div>
      </div>

      {board.isLoading || !board.data ? (
        <Skeleton active paragraph={{ rows: 10 }} />
      ) : (
        <div className="board">
          {board.data.columns.map((column, index) => {
            const color = column.stage.type === 'LOST' ? '#e5484d' : STAGE_COLORS[Math.min(index, STAGE_COLORS.length - 1)]
            return (
              <section
                key={column.stage.id}
                className={`board-col${dropTarget === column.stage.id ? ' drop-target' : ''}`}
                style={{ ['--stage-color' as string]: color }}
                onDragOver={(e) => {
                  if (!dragging) return
                  e.preventDefault()
                  setDropTarget(column.stage.id)
                }}
                onDragLeave={() => setDropTarget(null)}
                onDrop={(e) => {
                  e.preventDefault()
                  setDropTarget(null)
                  const deal = e.dataTransfer.getData('text/plain')
                  if (deal && !column.deals.some((d) => d.id === deal)) move.mutate({ deal, stageId: column.stage.id })
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Typography.Text strong style={{ color: brand.navy }}>
                    {column.stage.name}
                  </Typography.Text>
                  <span className="muted" style={{ fontSize: 12 }}>
                    {column.stage.probability}%
                  </span>
                  <span style={{ marginLeft: 'auto', fontWeight: 600 }}>{column.count}</span>
                  {can('deals', 'CREATE') && (
                    <Tooltip title="New deal in this stage">
                      <Button
                        type="text"
                        size="small"
                        icon={<PlusOutlined />}
                        onClick={() => setCreating({ pipelineId: board.data.pipelineId, stageId: column.stage.id })}
                      />
                    </Tooltip>
                  )}
                </div>
                <div className="muted" style={{ fontSize: 12 }}>
                  {money(column.totalAmount)} · weighted {money(column.weightedAmount)}
                </div>

                {column.deals.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No deals" style={{ margin: '16px 0' }} />}
                {column.deals.map((deal) => (
                  <div
                    key={deal.id}
                    className="board-card"
                    draggable={canMove}
                    onDragStart={(e) => {
                      e.dataTransfer.setData('text/plain', deal.id)
                      setDragging(deal.id)
                    }}
                    onDragEnd={() => setDragging(null)}
                    style={{ opacity: dragging === deal.id ? 0.5 : 1 }}
                  >
                    <Link to={`/deals/${deal.id}`}>
                      <strong>{deal.title}</strong>
                    </Link>
                    <div className="muted" style={{ fontSize: 13 }}>
                      {deal.labels.companyId ?? 'No company'}
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', marginTop: 8, gap: 8 }}>
                      <strong style={{ color: brand.navy }}>{money(Number(deal.values.amount ?? 0))}</strong>
                      <span className="muted" style={{ fontSize: 12 }}>
                        {deal.values.expectedCloseDate ? `closes ${formatDate(deal.values.expectedCloseDate)}` : ''}
                      </span>
                      <Tooltip title={deal.labels.ownerId}>
                        <Avatar size="small" className="avatar-brand" style={{ marginLeft: 'auto' }}>
                          {initials(deal.labels.ownerId)}
                        </Avatar>
                      </Tooltip>
                    </div>
                  </div>
                ))}
                {column.count > column.deals.length && (
                  <div className="muted" style={{ marginTop: 10, fontSize: 12 }}>
                    + {column.count - column.deals.length} more in the list view
                  </div>
                )}
              </section>
            )
          })}
        </div>
      )}

      {creating && (
        <RecordFormDrawer
          module="deals"
          initialValues={creating}
          onClose={() => setCreating(null)}
          onSaved={() => void queryClient.invalidateQueries({ queryKey: ['board'] })}
        />
      )}
    </div>
  )
}
