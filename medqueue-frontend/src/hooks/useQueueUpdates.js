import { useEffect, useState } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { mergeQueueSnapshot, subscribeToQueueTopics } from '../lib/adminQueue'

export function useQueueSubscriptions(token, doctorIds) {
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
        subscribeToQueueTopics(client, ids, (snapshot) => {
          setSnapshots((current) => mergeQueueSnapshot(current, snapshot))
        })
      },
      onWebSocketClose: () => setConnected(false), onStompError: () => setConnected(false),
    })
    client.activate(); return () => { disposed = true; client.deactivate(); setConnected(false) }
  }, [token, doctorKey])
  return { snapshots, connected }
}

export function useQueueUpdates(token, doctorId) {
  const { snapshots, connected } = useQueueSubscriptions(token, doctorId ? [doctorId] : [])
  return { snapshot: doctorId ? snapshots[String(doctorId)] || null : null, connected }
}
