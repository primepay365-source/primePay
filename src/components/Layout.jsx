import { useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import {
  LayoutGrid, Wallet, BarChart3, Users, Bell, HelpCircle, User, FileText,
  LogOut, ChevronLeft, ChevronRight, X, ExternalLink, MessageCircle,
} from 'lucide-react'
import logo from '../logo.png'
import { useAuth } from '../auth/AuthContext'

const businessRoles = ['SUPER_DISTRIBUTOR', 'MASTER_DISTRIBUTOR', 'DISTRIBUTOR', 'RETAILER']

const networkLabels = {
  ADMIN: 'All Accounts',
  SUPER_DISTRIBUTOR: 'Master Distributors',
  MASTER_DISTRIBUTOR: 'Distributors',
  DISTRIBUTOR: 'Retailers',
}

const sections = [
  { items: [{ to: '/', label: 'Dashboard', icon: LayoutGrid }] },
  {
    title: 'MONEY',
    items: [
      { to: '/wallet', label: 'Wallet', icon: Wallet },
      { to: '/reports', label: 'Reports', icon: BarChart3 },
    ],
  },
  { title: 'NETWORK', items: [{ to: '/master-distributors', label: 'All Accounts', icon: Users }] },
  {
    title: 'ACCOUNT',
    items: [
      { to: '/notifications', label: 'Notifications', icon: Bell, badge: 5 },
      { to: '/help', label: 'Help & Support', icon: HelpCircle },
      { to: '/profile', label: 'Profile & Settings', icon: User },
      { to: '/pdf-generation', label: 'Pdf Generation', icon: FileText },
    ],
  },
]

const titles = {
  '/': ['Dashboard', 'Overview of your transactions.'],
  '/wallet': ['Wallet', 'Add funds, withdraw and track commissions.'],
  '/reports': ['Reports', 'Funds and commission statements'],
  '/master-distributors': ['Master Distributors', 'Master distributors onboarded by you'],
  '/distributors/onboard': ['Create Distributor', 'Complete the distributor onboarding steps'],
  '/notifications': ['Notifications', 'Updates from admin and your account'],
  '/help': ['Help & Support', 'Raise and track issues with your transactions'],
  '/profile': ['Profile & Settings', 'Your account details and security'],
  '/pdf-generation': ['Pdf Generation', 'Generate and download PDFs'],
}

export default function Layout() {
  const [collapsed, setCollapsed] = useState(false)
  const [banner, setBanner] = useState(true)
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const { session, logout } = useAuth()
  const account = session.account
  const [title, sub] = titles[pathname] || titles['/']
  const canTransact = businessRoles.includes(account.role)
  const visibleSections = sections
    .map((section) => ({
      ...section,
      items: section.items
        .filter(({ to }) => canTransact || (to !== '/wallet' && to !== '/reports'))
        .map((item) => item.to === '/master-distributors'
          ? { ...item, label: networkLabels[account.role] || 'My Network' }
          : item),
    }))
    .filter((section) => section.items.length > 0)

  return (
    <div className="shell">
      <aside className={`sidebar ${collapsed ? 'collapsed' : ''}`}>
        <div className="logo-box">
          <img className="logo-img" src={logo} alt="Payswitch" />
        </div>
        <button className="collapse-btn" onClick={() => setCollapsed(!collapsed)} aria-label="Toggle sidebar">
          {collapsed ? <ChevronRight size={16} /> : <ChevronLeft size={16} />}
        </button>
        <nav className="nav">
          {visibleSections.map((s, i) => (
            <div key={i}>
              {s.title && <div className="nav-section">{s.title}</div>}
              {s.items.map(({ to, label, icon: Icon, badge }) => (
                <NavLink key={to} to={to} end={to === '/'} className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
                  <Icon size={20} />
                  <span className="nav-label">{label}</span>
                  {badge && <span className="nav-badge">{badge}</span>}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
        <div className="logout">
          <button onClick={() => { logout(); navigate('/login', { replace: true }) }}>
            <LogOut size={20} />
            <span className="nav-label">Logout</span>
          </button>
        </div>
      </aside>

      <div className="main">
        {banner && (
          <div className="banner">
            <MessageCircle size={24} />
            <span>Follow the Payswitch Technologies Pvt Ltd channel on WhatsApp</span>
            <a href="#follow">Follow <ExternalLink size={14} /></a>
            <button className="close" onClick={() => setBanner(false)} aria-label="Close banner"><X size={20} /></button>
          </div>
        )}
        <header className="topbar">
          <div>
            <h1>{title}</h1>
            <p>{sub}</p>
          </div>
          <div className="spacer" />
          <div className="funds-pill">Funds ₹ 0.00</div>
          <button className="bell" onClick={() => navigate('/notifications')} aria-label="Notifications">
            <Bell size={20} />
            <span className="dot">5</span>
          </button>
          <div className="user">
            <div className="avatar">{account.displayName?.slice(0, 1)?.toUpperCase() || 'U'}</div>
            <div>
              <div className="uname">{account.displayName}</div>
              <div className="uid">{account.accountCode} · {account.role?.replaceAll('_', ' ')}</div>
            </div>
          </div>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
