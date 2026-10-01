import { useEffect, useState } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

export function useQueueUpdates(token, doctorId) {
  const [snapshot, setSnapshot] = useState(null); const [connected, setConnected] = useState(false)
  useEffect(() => {
    setSnapshot(null); setConnected(false)
    if (!token || !doctorId) return undefined
    const client = new Client({
      webSocketFactory: () => new SockJS(new URL(import.meta.env.VITE_WS_URL || '/ws', window.location.href).toString()),
      connectHeaders: { Authorization: `Bearer ${token}` }, reconnectDelay: 5000,
      onConnect: () => { setConnected(true); client.subscribe(`/topic/queue/${doctorId}`, (message) => setSnapshot(JSON.parse(message.body))) },
      onWebSocketClose: () => setConnected(false), onStompError: () => setConnected(false),
    })
    client.activate(); return () => { client.deactivate(); setConnected(false) }
  }, [token, doctorId])
  return { snapshot, connected }
}
