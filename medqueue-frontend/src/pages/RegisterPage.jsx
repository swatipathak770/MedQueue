import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, errorMessage } from '../api/client'
import { Notice } from '../components/Primitives'
import { AuthFrame } from './LoginPage'

export default function RegisterPage() {
  const navigate = useNavigate(); const [form, setForm] = useState({ name: '', email: '', phone: '', password: '' }); const [error, setError] = useState(''); const [busy, setBusy] = useState(false)
  const submit = async (e) => { e.preventDefault(); setError(''); setBusy(true); try { await api.post('/api/auth/register', form); navigate('/login', { state: { registered: true } }) } catch (e2) { setError(errorMessage(e2)) } finally { setBusy(false) } }
  return <AuthFrame eyebrow="Start with MedQueue" title="Create your account" copy="A patient account lets you find doctors, reserve a slot, and follow your queue live.">
    <form className="space-y-4" onSubmit={submit}>{error && <Notice>{error}</Notice>}
      <label className="field-label">Full name<input className="field" autoComplete="name" required maxLength="120" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
      <label className="field-label">Email address<input className="field" autoComplete="email" type="email" required value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></label>
      <label className="field-label">Phone <span className="font-normal text-slate-400">(optional)</span><input className="field" autoComplete="tel" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} /></label>
      <label className="field-label">Password<input className="field" autoComplete="new-password" type="password" minLength="8" maxLength="72" required value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} /><span className="mt-1 block text-xs font-normal text-slate-400">Use at least 8 characters.</span></label>
      <button className="button-primary w-full" disabled={busy}>{busy ? 'Creating account…' : 'Create account'} <span>→</span></button>
    </form>
    <div className="mt-6 text-center text-sm text-slate-500">Already registered? <Link className="font-semibold text-teal hover:underline" to="/login">Sign in</Link></div>
  </AuthFrame>
}
