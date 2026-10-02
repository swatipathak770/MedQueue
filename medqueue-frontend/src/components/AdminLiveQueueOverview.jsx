import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSelector } from 'react-redux'
import { api, errorMessage } from '../api/client'
import { useQueueSubscriptions } from '../hooks/useQueueUpdates'
import { EmptyState, Notice, Panel } from './Primitives'

export default function AdminLiveQueueOverview() {
  const token = useSelector((state) => state.auth.token)
  const [doctors, setDoctors] = useState([])
  const [initialLoading, setInitialLoading] = useState(true)
  const [error, setError] = useState('')
  const doctorIds = useMemo(() => doctors.map((doctor) => doctor.doctorId), [doctors])
  const refreshBaseline = useCallback(() => api.get('/api/admin/queues')
    .then(({ data }) => { setDoctors(data); setError('') })
    .catch((failure) => { setError(errorMessage(failure)) }), [])
  const { snapshots, connected } = useQueueSubscriptions(token, doctorIds, refreshBaseline)

  useEffect(() => {
    let active = true
    const refresh = () => api.get('/api/admin/queues')
      .then(({ data }) => { if (active) setDoctors(data) })
      .catch((failure) => { if (active) setError(errorMessage(failure)) })
      .finally(() => { if (active) setInitialLoading(false) })
    refresh()
    return () => { active = false }
  }, [])

  const rows = doctors.map((doctor) => ({
    ...doctor,
    ...(snapshots[String(doctor.doctorId)] || {}),
  }))

  return <Panel title="Live queue overview" hint="Current queue activity across every doctor. Patient names and appointment details are not shown."
    action={<span className="pill"><span className={connected ? 'live-dot' : 'offline-dot'} />{connected ? 'Live updates' : 'Connecting'}</span>}>
    {error && <div className="mb-4"><Notice>{error}</Notice></div>}
    {initialLoading ? <div className="skeleton h-24" /> : rows.length === 0
      ? <EmptyState title="No doctors configured">Doctor queues will appear here after an administrator creates doctor accounts.</EmptyState>
      : <div className="overflow-x-auto"><table className="data-table">
        <thead><tr><th>Doctor</th><th>Department</th><th>Waiting</th><th>Currently serving</th><th>Queue status</th></tr></thead>
        <tbody>{rows.map((doctor) => {
          const status = doctor.queueOpen ? 'Open' : 'Closed'
          const availability = doctor.available ? 'Available' : 'Unavailable'
          const active = doctor.activeTokenNumber
            ? `#${doctor.activeTokenNumber} · ${doctor.activeStatus === 'IN_PROGRESS' ? 'In progress' : 'Called'}`
            : '—'
          return <tr key={doctor.doctorId}>
            <td className="font-semibold text-ink">Dr. {doctor.doctorName}</td>
            <td>{doctor.department}</td>
            <td>{doctor.waitingCount}</td>
            <td>{active}</td>
            <td><span className={`status ${doctor.queueOpen && doctor.available ? 'status-done' : 'status-skipped'}`}>{status} · {availability}</span></td>
          </tr>
        })}</tbody>
      </table></div>}
  </Panel>
}
