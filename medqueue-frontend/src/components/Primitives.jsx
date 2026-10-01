export function Panel({ title, hint, action, children, className = '' }) {
  return <section className={`panel ${className}`}><div className="mb-5 flex items-start justify-between gap-3"><div><h2 className="font-display text-lg font-bold text-ink">{title}</h2>{hint && <p className="mt-1 text-sm text-slate-500">{hint}</p>}</div>{action}</div>{children}</section>
}
export function Notice({ children, tone = 'error' }) { return <div className={`notice notice-${tone}`} role="status">{children}</div> }
export function StatusPill({ status }) {
  const value = (status || 'unknown').toLowerCase().replace('_', ' ')
  return <span className={`status status-${value.replaceAll(' ', '-')}`}>{value}</span>
}
export function EmptyState({ title, children }) { return <div className="empty-state"><div className="mb-3 text-2xl">◌</div><div className="font-semibold text-ink">{title}</div><div className="mt-1 text-sm text-slate-500">{children}</div></div> }
