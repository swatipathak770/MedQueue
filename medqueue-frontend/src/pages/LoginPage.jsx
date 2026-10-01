import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import { api, errorMessage } from '../api/client'
import { signedIn } from '../state/authSlice'
import { Notice } from '../components/Primitives'

export default function LoginPage() {
  const dispatch = useDispatch(); const navigate = useNavigate()
  const [form, setForm] = useState({ email: '', password: '' }); const [error, setError] = useState(''); const [busy, setBusy] = useState(false)
  const submit = async (e) => {
    e.preventDefault(); setError(''); setBusy(true)
    try { const { data } = await api.post('/api/auth/login', form); dispatch(signedIn(data)); navigate(`/${data.user.role.toLowerCase()}`) }
    catch (e2) { setError(errorMessage(e2)) } finally { setBusy(false) }
  }
  return <AuthFrame eyebrow="Your clinic, made simpler" title="Welcome back" copy="Sign in to book a visit or pick up right where your care journey left off.">
    <form className="space-y-4" onSubmit={submit}>
      {error && <Notice>{error}</Notice>}
      <label className="field-label">Email address<input className="field" autoComplete="email" type="email" required value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} placeholder="you@example.com" /></label>
      <label className="field-label">Password<input className="field" autoComplete="current-password" type="password" required value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} placeholder="Your password" /></label>
      <button className="button-primary w-full" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'} <span>→</span></button>
    </form>
    <div className="mt-6 text-center text-sm text-slate-500">New to MedQueue? <Link className="font-semibold text-teal hover:underline" to="/register">Create a patient account</Link></div>
  </AuthFrame>
}

export function AuthFrame({ eyebrow, title, copy, children }) {
  return <div className="auth-page"><div className="auth-brand"><Link to="/login" className="flex items-center gap-3"><div className="grid h-11 w-11 place-items-center rounded-2xl bg-white text-xl font-bold text-teal shadow-soft">M</div><div><div className="font-display text-xl font-bold text-white">MedQueue</div><div className="text-[11px] uppercase tracking-[.18em] text-white/60">Smart clinic flow</div></div></Link><div className="mt-auto hidden max-w-sm pb-8 text-white md:block"><div className="mb-3 text-xs font-bold uppercase tracking-[.22em] text-teal-200">Less waiting. More certainty.</div><p className="font-display text-3xl font-semibold leading-snug">A clearer way to move through your day at the clinic.</p></div></div><div className="auth-content"><div className="w-full max-w-md"><div className="eyebrow">{eyebrow}</div><h1 className="mt-3 font-display text-3xl font-bold tracking-tight text-ink">{title}</h1><p className="mb-8 mt-2 text-sm leading-6 text-slate-500">{copy}</p><div className="panel">{children}</div><p className="mt-6 text-center text-xs text-slate-400">Your health information stays protected.</p></div></div></div>
}
