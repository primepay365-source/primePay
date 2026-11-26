import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth/AuthContext'
import Layout from './components/Layout'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Wallet from './pages/Wallet'
import Reports from './pages/Reports'
import MasterDistributors from './pages/MasterDistributors'
import DistributorOnboarding from './pages/DistributorOnboarding'
import Notifications from './pages/Notifications'
import Help from './pages/Help'
import Profile from './pages/Profile'
import PdfGeneration from './pages/PdfGeneration'

function RequireAuth({ children }) {
  const { session } = useAuth()
  const location = useLocation()

  if (!session?.token) return <Navigate to="/login" replace state={{ from: location }} />
  return children
}

function RequireBusiness({ children }) {
  const { session } = useAuth()
  const businessRoles = ['SUPER_DISTRIBUTOR', 'MASTER_DISTRIBUTOR', 'DISTRIBUTOR', 'RETAILER']
  if (!businessRoles.includes(session?.account?.role)) return <Navigate to="/" replace />
  return children
}

function AppRoutes() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/" element={<RequireAuth><Layout /></RequireAuth>}>
          <Route index element={<Dashboard />} />
          <Route path="wallet" element={<RequireBusiness><Wallet /></RequireBusiness>} />
          <Route path="reports" element={<RequireBusiness><Reports /></RequireBusiness>} />
          <Route path="master-distributors" element={<MasterDistributors />} />
          <Route path="distributors/onboard" element={<DistributorOnboarding />} />
          <Route path="notifications" element={<Notifications />} />
          <Route path="help" element={<Help />} />
          <Route path="profile" element={<Profile />} />
          <Route path="pdf-generation" element={<PdfGeneration />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  )
}

export default function App() {
  return <BrowserRouter><AppRoutes /></BrowserRouter>
}
