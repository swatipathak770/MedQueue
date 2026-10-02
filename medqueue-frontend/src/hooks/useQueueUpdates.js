import { useEffect, useState } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { createQueueSubscriptionManager, mergeQueueSnapshot } from '../lib/adminQueue'

export function useQueueSubscriptions(token, doctorIds, refreshBaseline) {
  const doctorKey = [...new Set(doctorIds.filter((id) => id !== null && id !== undefined).map(String))].sort().join(',')
  const [snapshots, setSnapshots] = useState({}); const [connected, setConnected] = useState(false)
  useEffect(() => {
    setSnapshots({}); setConnected(false)
    const ids = doctorKey ? doctorKey.split(',') : []
    if (!token || ids.length === 0) return undefined
    let disposed = false
    const client = new Client({
      webSocketFactory: () => new SockJS(new URL(import.meta.env.VITE_WS_URL || '/ws', window.location.href).toString()),
      connectHeaders: { Authorization: `Bearer ${token}` }, reconnectDelay: 5000,
      onConnect: () => {
        if (disposed) return
        setConnected(true)
        setSnapshots({})
        subscriptions.onConnect()
      },
      onWebSocketClose: () => {
        subscriptions.onDisconnect()
        setConnected(false)
      }, onStompError: () => setConnected(false),
    })
    const subscriptions = createQueueSubscriptionManager(client, (snapshot) => {
          setSnapshots((current) => mergeQueueSnapshot(current, snapshot))
        }, refreshBaseline)
    subscriptions.setDoctorIds(ids)
    client.activate(); return () => { disposed = true; subscriptions.dispose(); client.deactivate(); setConnected(false) }
  }, [token, doctorKey, refreshBaseline])
  return { snapshots, connected }
}

export function useQueueUpdates(token, doctorId, refreshBaseline) {
  const { snapshots, connected } = useQueueSubscriptions(token, doctorId ? [doctorId] : [], refreshBaseline)
  return { snapshot: doctorId ? snapshots[String(doctorId)] || null : null, connected }
}
