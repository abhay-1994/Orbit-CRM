import { useMutation, useQueryClient } from '@tanstack/react-query'
import { http } from '../../api/client'
import type { RecordView } from '../../api/types'
import { toast } from '../../lib/feedback'
import { usePipelines } from '../../lib/hooks'

/** The deal's pipeline as clickable chevrons; clicking a stage moves the deal there. */
export function StagePath({ deal, editable }: { deal: RecordView; editable: boolean }) {
  const queryClient = useQueryClient()
  const pipelines = usePipelines()
  const move = useMutation({
    mutationFn: (stageId: string) => http.patch<RecordView>(`/deals/${deal.id}`, { stageId, version: deal.version }),
    onSuccess: (updated) => {
      toast.success(`Moved to ${updated.labels.stageId}`)
      for (const key of [['record', 'deals', deal.id], ['records', 'deals'], ['timeline'], ['board'], ['stage-history', deal.id]]) {
        void queryClient.invalidateQueries({ queryKey: key })
      }
    },
  })

  const pipeline = pipelines.data?.find((p) => p.id === deal.values.pipelineId)
  if (!pipeline) return null
  const stages = pipeline.stages.filter((s) => s.active || s.id === deal.values.stageId)
  const current = stages.findIndex((s) => s.id === deal.values.stageId)

  return (
    <div className="stage-path">
      {stages.map((stage, index) => {
        const state = index === current ? `current ${stage.type.toLowerCase()}` : index < current && stage.type === 'OPEN' ? 'done' : ''
        return (
          <button
            key={stage.id}
            type="button"
            className={`chevron ${state}`}
            disabled={!editable || index === current || move.isPending}
            onClick={() => move.mutate(stage.id)}
            title={`${stage.name} · ${stage.probability}% probability`}
          >
            {stage.name}
          </button>
        )
      })}
    </div>
  )
}
