import { useId } from 'react'
import { brand } from '../theme'

interface Bar {
  label: string
  value: number
  tooltip: string
}

/** Small dependency-free bar chart with a brand-gradient fill. */
export function BarChart({ data, height = 220, formatAxis }: { data: Bar[]; height?: number; formatAxis: (v: number) => string }) {
  const gradientId = useId()
  const top = niceCeiling(Math.max(0, ...data.map((d) => d.value)))
  const slot = 48
  const left = 56
  const width = left + data.length * slot
  const plot = height - 28
  const y = (v: number) => 8 + (plot - 8) * (1 - v / top)

  return (
    <svg viewBox={`0 0 ${width} ${height}`} width="100%" role="img" aria-label="Bar chart">
      <defs>
        <linearGradient id={gradientId} x1="0" y1="1" x2="0" y2="0">
          <stop offset="0" stopColor={brand.navy} />
          <stop offset="0.6" stopColor={brand.purple} />
          <stop offset="1" stopColor={brand.cyan} />
        </linearGradient>
      </defs>
      {[0, 0.25, 0.5, 0.75, 1].map((t) => (
        <g key={t}>
          <line x1={left} x2={width} y1={y(top * t)} y2={y(top * t)} stroke="#eef0f6" />
          <text x={left - 8} y={y(top * t) + 4} textAnchor="end" fontSize="11" fill="#8a91ab">
            {formatAxis(top * t)}
          </text>
        </g>
      ))}
      {data.map((d, i) => {
        const x = left + i * slot + slot * 0.22
        const barTop = y(d.value)
        return (
          <g key={d.label}>
            <rect x={x} y={barTop} width={slot * 0.56} height={Math.max(0, plot - barTop)} rx="4" fill={`url(#${gradientId})`}>
              <title>{d.tooltip}</title>
            </rect>
            <text x={x + slot * 0.28} y={height - 8} textAnchor="middle" fontSize="11" fill="#6b7394">
              {d.label}
            </text>
          </g>
        )
      })}
    </svg>
  )
}

/** Rounds up to 1, 2 or 5 times a power of ten, so axis labels are readable. */
function niceCeiling(value: number): number {
  if (value <= 0) return 1
  const power = 10 ** Math.floor(Math.log10(value))
  const step = [1, 2, 5, 10].find((s) => s * power >= value) ?? 10
  return step * power
}
