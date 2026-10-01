import { useDispatch, useSelector } from 'react-redux'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { signedOut } from '../state/authSlice'

const titles = { PATIENT: ['Patient portal', 'Find care and follow your place in line.'], DOCTOR: ['Doctor workspace', 'Your clinic queue, updated live.'], ADMIN: ['Clinic overview', 'Manage your team and clinic schedule.'] }
export function AppShell({ children }) {
  const user = useSelector((s) => s.auth.user)
  const dispatch = useDispatch(); const navigate = useNavigate(); const location = useLocation()
  const role = user?.role || 'PATIENT'; const [title, description] = titles[role]
  const leave = () => { dispatch(signedOut()); navigate('/login') }
  return <div className="min-h-screen bg-mist text-slate-700">
    <header className="border-b border-slate-200/80 bg-white/90 backdrop-blur">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-5 py-4 lg:px-8">
        <Link to={`/${role.toLowerCase()}`} className="flex items-center gap-3">
          <div className="grid h-10 w-10 place-items-center rounded-2xl bg-teal text-lg font-bold text-white">M</div>
          <div><div className="font-display text-lg font-bold tracking-tight text-ink">MedQueue</div><div className="text-[11px] font-semibold uppercase tracking-[.18em] text-slate-400">Smart clinic flow</div></div>
        </Link>
        <div className="flex items-center gap-4">
          <div className="hidden text-right sm:block"><div className="text-sm font-semibold text-ink">{user?.name}</div><div className="text-xs text-slate-500">{title}</div></div>
          <div className="h-9 w-px bg-slate-200" />
          <button onClick={leave} className="rounded-xl px-3 py-2 text-sm font-semibold text-slate-500 transition hover:bg-slate-100 hover:text-ink">Sign out</button>
        </div>
      </div>
    </header>
    <main className="mx-auto max-w-7xl px-5 py-8 lg:px-8">
      <div className="mb-7 flex flex-wrap items-end justify-between gap-3"><div><div className="eyebrow">{role.toLowerCase()} / workspace</div><h1 className="mt-2 font-display text-3xl font-bold tracking-tight text-ink">{title}</h1><p className="mt-1 text-sm text-slate-500">{description}</p></div><div className="pill"><span className="live-dot" /> Secure session</div></div>
      {location.pathname && children}
    </main>
    <footer className="mx-auto max-w-7xl px-5 pb-6 text-xs text-slate-400 lg:px-8">MedQueue · Care, in order</footer>
  </div>
}
