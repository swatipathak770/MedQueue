export function queueTopicDestinations(doctorIds) {
  return [...new Set(doctorIds.filter((id) => id !== null && id !== undefined).map(String))]
    .map((id) => `/topic/queue/${id}`)
}

export function subscribeToQueueTopics(client, doctorIds, onSnapshot) {
  return queueTopicDestinations(doctorIds).map((destination) =>
    client.subscribe(destination, (message) => onSnapshot(JSON.parse(message.body))))
}

export function summarizeQueueSnapshot(snapshot) {
  const active = snapshot.queue?.find((entry) => ['CALLED', 'IN_PROGRESS'].includes(entry.status))
  return {
    doctorId: String(snapshot.doctorId),
    date: snapshot.date,
    available: snapshot.available,
    queueOpen: snapshot.open,
    currentTokenNumber: snapshot.currentTokenNumber,
    waitingCount: snapshot.waitingCount,
    activeTokenNumber: active?.tokenNumber ?? null,
    activeStatus: active?.status ?? null,
    updatedAt: snapshot.updatedAt,
  }
}

export function mergeQueueSnapshot(current, snapshot) {
  const summary = summarizeQueueSnapshot(snapshot)
  const existing = current[summary.doctorId]
  if (existing?.updatedAt && summary.updatedAt && existing.updatedAt > summary.updatedAt) return current
  return { ...current, [summary.doctorId]: { ...existing, ...summary } }
}
