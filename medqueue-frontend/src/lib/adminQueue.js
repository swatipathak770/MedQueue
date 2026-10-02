export function queueTopicDestinations(doctorIds) {
  return [...new Set(doctorIds.filter((id) => id !== null && id !== undefined).map(String))]
    .map((id) => `/topic/queue/${id}`)
}

export function subscribeToQueueTopics(client, doctorIds, onSnapshot) {
  return queueTopicDestinations(doctorIds).map((destination) =>
    client.subscribe(destination, (message) => onSnapshot(JSON.parse(message.body))))
}

// STOMP subscriptions belong to one socket session. Forget handles on close,
// then recreate them once for the next connected session.
export function createQueueSubscriptionManager(client, onSnapshot, refreshBaseline) {
  let requested = []
  let subscriptions = new Map()
  let connected = false

  const subscribeMissing = () => {
    for (const destination of requested) {
      if (subscriptions.has(destination)) continue
      const subscription = client.subscribe(destination, (message) => onSnapshot(JSON.parse(message.body)))
      subscriptions.set(destination, subscription)
    }
  }

  const removeUnwanted = () => {
    const wanted = new Set(requested)
    for (const [destination, subscription] of subscriptions) {
      if (wanted.has(destination)) continue
      subscription.unsubscribe()
      subscriptions.delete(destination)
    }
  }

  return {
    setDoctorIds(doctorIds) {
      requested = queueTopicDestinations(doctorIds)
      if (connected) {
        removeUnwanted()
        subscribeMissing()
      }
    },
    onConnect() {
      if (connected) return
      connected = true
      subscribeMissing()
      Promise.resolve(refreshBaseline?.()).catch(() => {})
    },
    onDisconnect() {
      connected = false
      // The closed STOMP session has already released its server subscriptions.
      subscriptions = new Map()
    },
    dispose() {
      if (connected) {
        for (const subscription of subscriptions.values()) subscription.unsubscribe()
      }
      subscriptions.clear()
      connected = false
      requested = []
    },
  }
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
