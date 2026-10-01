import { Navigate, Route, Routes } from 'react-router-dom'
import { useSelector } from 'react-redux'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import PatientDashboard from './pages/PatientDashboard'
import DoctorDashboard from './pages/DoctorDashboard'
import AdminDashboard from './pages/AdminDashboard'
import { AppShell } from './components/AppShell'

function Guard({ roles, children }) {
  const { token, user } = useSelector((s) => s.auth)
  if (!token || !user) return <Navigate to="/login" replace />
  if (roles && !roles.includes(user.role)) return <Navigate to={`/${user.role.toLowerCase()}`} replace />
  return <AppShell>{children}</AppShell>
}

function Landing() {
  const { token, user } = useSelector((s) => s.auth)
  return <Navigate to={token && user ? `/${user.role.toLowerCase()}` : '/login'} replace />
}

export default function App() {
  return <Routes>
    <Route path="/" element={<Landing />} />
    <Route path="/login" element={<LoginPage />} />
    <Route path="/register" element={<RegisterPage />} />
    <Route path="/patient/*" element={<Guard roles={['PATIENT']}><PatientDashboard /></Guard>} />
    <Route path="/doctor/*" element={<Guard roles={['DOCTOR']}><DoctorDashboard /></Guard>} />
    <Route path="/admin/*" element={<Guard roles={['ADMIN']}><AdminDashboard /></Guard>} />
    <Route path="*" element={<Landing />} />
  </Routes>
}
