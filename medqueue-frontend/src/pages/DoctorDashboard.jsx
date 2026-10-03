import { useCallback, useEffect, useState } from 'react'
import { useSelector } from 'react-redux'
import { api, errorMessage } from '../api/client'
import { useQueueUpdates } from '../hooks/useQueueUpdates'
import { EmptyState, Notice, Panel, StatusPill } from '../components/Primitives'
import { findActiveQueueAppointment, localDateString, nowServingToken } from '../lib/queue'

export default function DoctorDashboard() {
  const token = useSelector((s) => s.auth.token)
  const [queue, setQueue] = useState(null); const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [busy, setBusy] = useState(false)
  const refresh = useCallback(async () => { const { data } = await api.get('/api/doctor/queue'); setQueue(data) }, [])
  const refreshBaseline = useCallback(() => refresh().catch((failure) => setError(errorMessage(failure))), [refresh])
  const { snapshot, connected } = useQueueUpdates(token, queue?.doctorId, refreshBaseline)
  useEffect(() => { refresh().catch((e) => setError(errorMessage(e))).finally(() => setLoading(false)) }, [refresh])
  const mutate = async (url, method = 'post', body) => { setBusy(true); setError(''); try { await api({ url, method, data: body }); await refresh() } catch (e) { setError(errorMessage(e)) } finally { setBusy(false) } }
  const todayString = localDateString()
  const todaysSnapshot = snapshot?.date === todayString ? snapshot : null
  const current = todaysSnapshot || queue
  const entries = todaysSnapshot?.queue || queue?.appointments || []
  const waiting = entries.filter((a) => a.status === 'WAITING')
  const active = findActiveQueueAppointment(entries)
  const today = new Date().toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' })
  return <div className="space-y-6">
    {error && <Notice>{error}</Notice>}
    {loading ? <div className="skeleton h-48" /> : !queue ? <Panel title="Doctor profile unavailable"><EmptyState title="No doctor profile is linked to this account">Ask an administrator to finish setting up your doctor profile.</EmptyState></Panel> : <>
      <div className="grid gap-4 sm:grid-cols-3"><Metric label="Waiting" value={snapshot?.waitingCount ?? waiting.length} note="patients in line" /><Metric label="Now serving" value={nowServingToken(entries)} note={active ? active.status.toLowerCase().replace('_', ' ') : 'no active patient'} /><Metric label="Queue state" value={current?.open ? 'Open' : 'Closed'} note={current?.available ? 'Available' : 'Marked unavailable'} /></div>
      <Panel title="Today’s queue" hint={today} action={<span className="pill"><span className={connected ? 'live-dot' : 'offline-dot'} />{connected ? 'Live updates' : 'Connecting'}</span>}>
        <div className="mb-5 flex flex-wrap gap-2">
          <button className="button-primary" disabled={busy || !current?.available || !current?.open || !!active} onClick={() => mutate('/api/doctor/queue/next')}>Call next patient <span>→</span></button>
          {active && <button className="button-secondary" disabled={busy} onClick={() => mutate(`/api/doctor/queue/${active.appointmentId || active.id}/complete`)}>Complete current visit</button>}
          {active && <button className="button-secondary" disabled={busy} onClick={() => mutate(`/api/doctor/queue/${active.appointmentId || active.id}/skip`)}>Skip / no-show</button>}
          <button className="button-secondary" disabled={busy} onClick={() => mutate('/api/doctor/queue/availability', 'put', { available: !current?.available })}>{current?.available ? 'Mark unavailable' : 'Mark available'}</button>
          <button className="button-quiet" disabled={busy || !current?.open} onClick={() => { if (window.confirm('Close today’s queue? New calls will be stopped for the rest of today.')) mutate('/api/doctor/queue/close') }}>Close today’s queue</button>
        </div>
        {entries.length === 0 ? <EmptyState title="The queue is clear">New appointments and walk-ins will appear here.</EmptyState> : <div className="overflow-x-auto"><table className="data-table"><thead><tr><th>Token</th><th>Position</th><th>Estimated wait</th><th>Status</th></tr></thead><tbody>{entries.map((a) => <tr key={a.appointmentId || a.id}><td className="font-display text-lg font-bold text-ink">#{a.tokenNumber}</td><td>{a.position || '—'}</td><td>{a.estimatedWaitMinutes ? `${a.estimatedWaitMinutes} min` : '—'}</td><td><StatusPill status={a.status} /></td></tr>)}</tbody></table></div>}
      </Panel>
    </>}
  </div>
}
function Metric({ label, value, note }) { return <div className="metric-card"><div className="text-xs font-bold uppercase tracking-[.16em] text-slate-400">{label}</div><div className="mt-2 font-display text-3xl font-bold text-ink">{value}</div><div className="mt-1 text-sm text-slate-500">{note}</div></div> }
