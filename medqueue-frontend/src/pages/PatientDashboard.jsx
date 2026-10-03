import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSelector } from 'react-redux'
import { api, errorMessage } from '../api/client'
import { useQueueUpdates } from '../hooks/useQueueUpdates'
import { EmptyState, Notice, Panel, StatusPill } from '../components/Primitives'
import { findLiveQueueEntry, isActiveAppointment, localDateString, patientVisitConnectionLabel } from '../lib/queue'

const today = () => localDateString()
const formatDate = (date) => new Date(`${date}T00:00:00`).toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' })

export default function PatientDashboard() {
  const token = useSelector((s) => s.auth.token)
  const [departments, setDepartments] = useState([]); const [doctors, setDoctors] = useState([]); const [history, setHistory] = useState([])
  const [slots, setSlots] = useState([]); const [department, setDepartment] = useState(''); const [date, setDate] = useState(today()); const [doctorId, setDoctorId] = useState(''); const [slotId, setSlotId] = useState('')
  const [walkIn, setWalkIn] = useState(false); const [busy, setBusy] = useState(false); const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [success, setSuccess] = useState('')
  const activeAppointment = useMemo(() => history.find((a) => isActiveAppointment(a, today())), [history])

  const refreshHistory = useCallback(async () => { const { data } = await api.get('/api/appointments/me'); setHistory(data) }, [])
  const { snapshot, connected } = useQueueUpdates(token, doctorId, refreshHistory)
  useEffect(() => { Promise.all([api.get('/api/departments'), api.get('/api/doctors'), refreshHistory()]).then(([d, docs]) => { setDepartments(d.data); setDoctors(docs.data) }).catch((e) => setError(errorMessage(e))).finally(() => setLoading(false)) }, [])
  useEffect(() => { if (activeAppointment) setDoctorId(String(activeAppointment.doctorId)) }, [activeAppointment])
  useEffect(() => { setSlots([]); setSlotId(''); if (!doctorId || !date) return; api.get(`/api/doctors/${doctorId}/slots`, { params: { date } }).then(({ data }) => setSlots(data)).catch((e) => setError(errorMessage(e))) }, [doctorId, date])

  const visibleDoctors = useMemo(() => doctors.filter((d) => !department || d.department.toLowerCase() === department.toLowerCase()), [doctors, department])
  const live = activeAppointment && snapshot?.date === activeAppointment.appointmentDate ? findLiveQueueEntry(snapshot, activeAppointment.id) : null
  const visitConnectionLabel = patientVisitConnectionLabel(activeAppointment, connected)
  const book = async (e) => { e.preventDefault(); setError(''); setSuccess(''); setBusy(true)
    try { const { data } = await api.post('/api/appointments', { doctorId: Number(doctorId), appointmentDate: date, slotId: walkIn ? null : Number(slotId), walkIn }); setSuccess(`Your token is #${data.tokenNumber}. You are ${data.queuePosition ? `number ${data.queuePosition} in line` : 'registered'}.`); setHistory((h) => [data, ...h]); setDoctorId(String(data.doctorId)); setSlotId(''); setWalkIn(false) }
    catch (e2) { setError(errorMessage(e2)) } finally { setBusy(false) }
  }
  return <div className="space-y-6">
    {(error || success) && <Notice tone={success ? 'success' : 'error'}>{success || error}</Notice>}
    <div className="grid gap-6 lg:grid-cols-[1.1fr_.9fr]">
      <Panel title="Find a visit" hint="Choose a doctor and a date to see the available schedule.">
        <form className="space-y-4" onSubmit={book}>
          <div className="grid gap-4 sm:grid-cols-2"><label className="field-label">Department<select className="field" value={department} onChange={(e) => setDepartment(e.target.value)}><option value="">All departments</option>{departments.map((d) => <option key={d.id} value={d.name}>{d.name}</option>)}</select></label>
            <label className="field-label">Visit date<input className="field" type="date" min={today()} value={date} onChange={(e) => setDate(e.target.value)} required /></label></div>
          <label className="field-label">Doctor<select className="field" value={doctorId} onChange={(e) => setDoctorId(e.target.value)} required><option value="">Choose a doctor</option>{visibleDoctors.map((d) => <option key={d.id} value={d.id}>Dr. {d.name} · {d.specialization}</option>)}</select></label>
          <div className="grid gap-3 sm:grid-cols-[1fr_auto] sm:items-end"><label className="field-label">Available time<select className="field" value={slotId} onChange={(e) => setSlotId(e.target.value)} disabled={!doctorId || walkIn} required={!walkIn}><option value="">Choose a slot</option>{slots.map((s) => <option key={s.id} value={s.id} disabled={!s.available}>{s.startTime.slice(0, 5)}–{s.endTime.slice(0, 5)} · {s.available ? `${s.remainingCapacity} left` : s.remainingCapacity === 0 ? 'Full' : 'Unavailable'}</option>)}</select></label>
            <button className={`button-secondary h-[46px] ${walkIn ? 'border-teal bg-teal/5 text-teal' : ''}`} type="button" aria-pressed={walkIn} onClick={() => { setWalkIn(!walkIn); setSlotId('') }}>Join walk-in queue</button></div>
          {doctorId && !walkIn && slots.length === 0 && <p className="text-xs text-slate-400">No template slots are listed for this weekday. You can still join the walk-in queue if it is open.</p>}
          <button className="button-primary w-full sm:w-auto" disabled={busy || !doctorId || (!walkIn && !slotId)}>{busy ? 'Booking…' : walkIn ? 'Join the queue' : 'Book appointment'} <span>→</span></button>
        </form>
      </Panel>
      <Panel title="Your live visit" hint="Queue changes appear here automatically." action={<span className="pill"><span className={activeAppointment && connected ? 'live-dot' : 'offline-dot'} />{visitConnectionLabel}</span>}>
        {!activeAppointment ? <EmptyState title="No active visit">Book an appointment or join a walk-in queue to get started.</EmptyState> : <div className="rounded-2xl bg-gradient-to-br from-ink to-[#22516a] p-5 text-white">
          <div className="flex items-start justify-between"><div><div className="text-xs font-semibold uppercase tracking-[.16em] text-white/60">Your token</div><div className="mt-1 font-display text-5xl font-bold">#{activeAppointment.tokenNumber}</div></div><StatusPill status={live?.status || activeAppointment.status} /></div>
          <div className="mt-5 grid grid-cols-2 gap-3 border-t border-white/15 pt-4"><div><div className="text-xs text-white/60">Position</div><div className="mt-1 text-xl font-bold">{live?.position ?? activeAppointment.queuePosition ?? '—'}<span className="ml-1 text-sm font-normal text-white/60">in line</span></div></div><div><div className="text-xs text-white/60">Estimated wait</div><div className="mt-1 text-xl font-bold">{live?.estimatedWaitMinutes ?? activeAppointment.estimatedWaitMinutes ?? '—'}<span className="ml-1 text-sm font-normal text-white/60">min</span></div></div></div>
          <div className="mt-4 text-sm text-white/70">Dr. {activeAppointment.doctorName} · {formatDate(activeAppointment.appointmentDate)}</div>
        </div>}
      </Panel>
    </div>
    <Panel title="Appointment history" hint="Your upcoming visits and completed care, in one place.">
      {loading ? <div className="skeleton h-20" /> : history.length === 0 ? <EmptyState title="No appointments yet">Your appointments will show here after your first booking.</EmptyState> : <div className="overflow-x-auto"><table className="data-table"><thead><tr><th>Doctor</th><th>Department</th><th>Date</th><th>Token</th><th>Status</th></tr></thead><tbody>{history.map((a) => <tr key={a.id}><td className="font-semibold text-ink">Dr. {a.doctorName}</td><td>{a.department}</td><td>{formatDate(a.appointmentDate)}</td><td>#{a.tokenNumber}</td><td><StatusPill status={a.status} /></td></tr>)}</tbody></table></div>}
    </Panel>
  </div>
}
