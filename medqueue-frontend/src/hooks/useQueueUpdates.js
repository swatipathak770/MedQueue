import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { createQueueSubscriptionManager, mergeQueueSnapshot, shouldConnectQueueSocket } from '../lib/adminQueue'

export function useQueueSubscriptions(token, doctorIds, refreshBaseline, connectWithoutDoctors = false) {
  const doctorKey = [...new Set(doctorIds.filter((id) => id !== null && id !== undefined).map(String))].sort().join(',')
  const connectionKey = connectWithoutDoctors ? '' : doctorKey
  const subscriptionsRef = useRef(null)
  const [snapshots, setSnapshots] = useState({}); const [connected, setConnected] = useState(false)

  useEffect(() => {
    const ids = doctorKey ? doctorKey.split(',') : []
    subscriptionsRef.current?.setDoctorIds(ids)
  }, [doctorKey])

  useEffect(() => {
    setSnapshots({}); setConnected(false)
    const ids = doctorKey ? doctorKey.split(',') : []
    if (!shouldConnectQueueSocket(token, ids, connectWithoutDoctors)) return undefined
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
      onDisconnect: () => {
        subscriptions.onDisconnect()
        setConnected(false)
      },
      onWebSocketClose: () => {
        subscriptions.onDisconnect()
        setConnected(false)
      }, onStompError: () => {
        subscriptions.onDisconnect()
        setConnected(false)
      },
    })
    const subscriptions = createQueueSubscriptionManager(client, (snapshot) => {
          setSnapshots((current) => mergeQueueSnapshot(current, snapshot))
        }, refreshBaseline)
    subscriptionsRef.current = subscriptions
    subscriptions.setDoctorIds(ids)
    client.activate(); return () => {
      disposed = true
      if (subscriptionsRef.current === subscriptions) subscriptionsRef.current = null
      subscriptions.dispose()
      client.deactivate()
      setConnected(false)
    }
  }, [token, connectionKey, refreshBaseline, connectWithoutDoctors])
  return { snapshots, connected }
}

export function useQueueUpdates(token, doctorId, refreshBaseline) {
  const { snapshots, connected } = useQueueSubscriptions(token, doctorId ? [doctorId] : [], refreshBaseline)
  return { snapshot: doctorId ? snapshots[String(doctorId)] || null : null, connected }
}
