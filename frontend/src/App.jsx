import { useState, useEffect } from 'react'
import LoginPage from './pages/LoginPage'
import MainPage from './pages/MainPage'
import AdminRelationsPage from './pages/AdminRelationsPage'
import AdminSuggestionsPage from './pages/AdminSuggestionsPage'
import { getGroups, getMe } from './api/client'

export default function App() {
  const [authed, setAuthed] = useState(null)  // null=checking, false=not authed, true=authed
  const [user, setUser] = useState(null)       // { username, role, permissions }
  const [currentPage, setCurrentPage] = useState('main')

  const checkAuth = () => {
    getMe()
      .then(u => { setUser(u); setAuthed(true) })
      .catch(() => setAuthed(false))
  }

  useEffect(() => {
    checkAuth()
    // Listen for 401 events fired by the axios interceptor
    const handler = () => { setAuthed(false); setUser(null) }
    window.addEventListener('fkblitz:unauthorized', handler)
    return () => window.removeEventListener('fkblitz:unauthorized', handler)
  }, [])

  const handleLogin = () => {
    // Fetch user info now that session is active
    getMe()
      .then(u => { setUser(u); setAuthed(true) })
      .catch(() => setAuthed(false))
  }

  const handleLogout = () => {
    setAuthed(false)
    setUser(null)
    setCurrentPage('main')
  }

  if (authed === null) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', background: 'var(--color-sidebar-bg)', color: '#64748b', fontSize: 14 }}>
        Loading…
      </div>
    )
  }

  if (!authed) {
    return <LoginPage onLogin={handleLogin} />
  }

  if (currentPage === 'admin-relations') {
    return <AdminRelationsPage onNavigate={setCurrentPage} />
  }

  if (currentPage === 'admin-suggestions') {
    return <AdminSuggestionsPage onNavigate={setCurrentPage} />
  }

  return (
    <MainPage
      onLogout={handleLogout}
      onNavigate={setCurrentPage}
      user={user}
    />
  )
}
