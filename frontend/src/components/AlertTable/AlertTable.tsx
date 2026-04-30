import { useState, useEffect } from 'react'
import { fetchAlerts } from '../../api/alerts'
import type { DetectionAlert, Severity, AlertType } from '../../types'

const SEVERITIES: Severity[] = ['HIGH', 'MED', 'LOW']
const ALERT_TYPES: AlertType[] = [
  'ALERT_BRUTE_FORCE',
  'ALERT_UNAUTHORIZED_MAC',
  'ALERT_PORT_SCAN',
  'ALERT_AFTER_HOURS',
]

export function AlertTable() {
  const [alerts, setAlerts] = useState<DetectionAlert[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [severity, setSeverity] = useState<Severity | ''>('')
  const [alertType, setAlertType] = useState<AlertType | ''>('')
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    setLoading(true)
    fetchAlerts({
      severity: severity || undefined,
      alertType: alertType || undefined,
      page,
      size: 10,
    })
      .then((res) => {
        setAlerts(res.data)
        setTotal(res.meta?.total ?? 0)
      })
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [severity, alertType, page])

  const totalPages = Math.ceil(total / 10)

  return (
    <div className="bg-slate-800 rounded-xl p-4">
      <div className="flex flex-wrap gap-3 mb-4 items-center">
        <h2 className="text-white font-semibold">Detection History</h2>
        <span className="text-slate-400 text-sm ml-auto">총 {total}건</span>

        <select
          className="bg-slate-700 text-white text-sm rounded px-2 py-1"
          value={severity}
          onChange={(e) => { setSeverity(e.target.value as Severity | ''); setPage(0) }}
        >
          <option value="">전체 심각도</option>
          {SEVERITIES.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>

        <select
          className="bg-slate-700 text-white text-sm rounded px-2 py-1"
          value={alertType}
          onChange={(e) => { setAlertType(e.target.value as AlertType | ''); setPage(0) }}
        >
          <option value="">전체 유형</option>
          {ALERT_TYPES.map((t) => <option key={t} value={t}>{t.replace('ALERT_', '')}</option>)}
        </select>
      </div>

      {loading ? (
        <p className="text-slate-400 text-sm">로딩 중...</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left text-slate-300">
            <thead className="text-xs uppercase text-slate-400 border-b border-slate-700">
              <tr>
                <th className="py-2 px-3">유형</th>
                <th className="py-2 px-3">심각도</th>
                <th className="py-2 px-3">IP</th>
                <th className="py-2 px-3">상세</th>
                <th className="py-2 px-3">탐지 시각</th>
              </tr>
            </thead>
            <tbody>
              {alerts.map((a) => (
                <tr key={a.id} className="border-b border-slate-700 hover:bg-slate-700/40">
                  <td className="py-2 px-3 font-mono text-xs text-red-300">
                    {a.alertType.replace('ALERT_', '')}
                  </td>
                  <td className="py-2 px-3">
                    <SeverityBadge severity={a.severity} />
                  </td>
                  <td className="py-2 px-3 text-sky-300 font-mono">{a.sourceIp}</td>
                  <td className="py-2 px-3 text-slate-400 max-w-xs truncate">{a.detail}</td>
                  <td className="py-2 px-3 text-slate-500 text-xs">
                    {new Date(a.detectedAt).toLocaleString('ko-KR')}
                  </td>
                </tr>
              ))}
              {alerts.length === 0 && (
                <tr>
                  <td colSpan={5} className="py-6 text-center text-slate-500">
                    탐지 이력이 없습니다
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex justify-center gap-2 mt-4">
          <button
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
            className="px-3 py-1 bg-slate-700 text-white rounded disabled:opacity-40 text-sm"
          >
            이전
          </button>
          <span className="text-slate-400 text-sm self-center">
            {page + 1} / {totalPages}
          </span>
          <button
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
            className="px-3 py-1 bg-slate-700 text-white rounded disabled:opacity-40 text-sm"
          >
            다음
          </button>
        </div>
      )}
    </div>
  )
}

function SeverityBadge({ severity }: { severity: string }) {
  const color =
    severity === 'HIGH' ? 'bg-red-600' :
    severity === 'MED'  ? 'bg-yellow-600' : 'bg-slate-600'
  return (
    <span className={`${color} text-white text-xs px-2 py-0.5 rounded-full`}>{severity}</span>
  )
}
