import { useState, useEffect } from 'react'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer, Cell,
} from 'recharts'
import { fetchIpStats } from '../../api/events'
import type { IpStat } from '../../types'

const COLORS = ['#ef4444', '#f97316', '#eab308', '#22c55e', '#3b82f6', '#8b5cf6']

export function IpChart() {
  const [stats, setStats] = useState<IpStat[]>([])
  const [loading, setLoading] = useState(false)

  const load = () => {
    setLoading(true)
    fetchIpStats()
      .then((data) => setStats(data.slice(0, 10)))
      .catch(console.error)
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    const timer = setInterval(load, 15_000) // 15초마다 갱신
    return () => clearInterval(timer)
  }, [])

  return (
    <div className="bg-slate-800 rounded-xl p-4">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-white font-semibold">IP별 이벤트 통계 (Top 10)</h2>
        <button
          onClick={load}
          className="text-sky-400 hover:text-sky-300 text-sm"
        >
          새로고침
        </button>
      </div>

      {loading && stats.length === 0 ? (
        <p className="text-slate-400 text-sm">로딩 중...</p>
      ) : stats.length === 0 ? (
        <p className="text-slate-500 text-sm">데이터 없음</p>
      ) : (
        <ResponsiveContainer width="100%" height={280}>
          <BarChart data={stats} margin={{ top: 5, right: 10, left: -10, bottom: 60 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
            <XAxis
              dataKey="ip"
              tick={{ fill: '#94a3b8', fontSize: 11 }}
              angle={-35}
              textAnchor="end"
              interval={0}
            />
            <YAxis tick={{ fill: '#94a3b8', fontSize: 11 }} allowDecimals={false} />
            <Tooltip
              contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #334155', borderRadius: 8 }}
              labelStyle={{ color: '#e2e8f0' }}
              itemStyle={{ color: '#94a3b8' }}
            />
            <Bar dataKey="count" name="이벤트 수" radius={[4, 4, 0, 0]}>
              {stats.map((_, i) => (
                <Cell key={i} fill={COLORS[i % COLORS.length]} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      )}
    </div>
  )
}
