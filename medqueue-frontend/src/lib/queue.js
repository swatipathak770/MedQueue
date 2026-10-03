export function isActiveAppointment(appointment, today) {
  return ['WAITING', 'CALLED', 'IN_PROGRESS'].includes(appointment.status) && appointment.appointmentDate >= today
}

export function patientVisitConnectionLabel(activeAppointment, connected) {
  if (!activeAppointment) return 'Not active'
  return connected ? 'Live' : 'Connecting'
}

export function findLiveQueueEntry(snapshot, appointmentId) {
  return snapshot?.queue?.find((entry) => entry.appointmentId === appointmentId) || null
}

export function localDateString(date = new Date()) {
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}
