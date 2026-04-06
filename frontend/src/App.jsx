import { useState, useEffect } from 'react'
import LoginPage from './pages/LoginPage'
import MainPage from './pages/MainPage'
import AdminRelationsPage from './pages/AdminRelationsPage'
import AdminSuggestionsPage from './pages/AdminSuggestionsPage'
import { getGroups } from './api/client'

export default function App() {
  const [authed, setAuthed] = useState(null)  // null = checking, false = not authed, true = authed
  const [currentPage, setCurrentPage] = useState('main')

  useEffect(() => {
    // Check if already logged in by hitting a protected endpoint
    getGroups()
      .then(() => setAuthed(true))
      .catch(() => setAuthed(false))
  }, [])

  if (authed === null) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', background: 'var(--color-sidebar-bg)', color: '#64748b', fontSize: 14 }}>
        Loading…
      </div>
    )
  }

  if (!authed) {
    return <LoginPage onLogin={() => setAuthed(true)} />
  }

  if (currentPage === 'admin-relations') {
    return <AdminRelationsPage onNavigate={setCurrentPage} />
  }

  if (currentPage === 'admin-suggestions') {
    return <AdminSuggestionsPage onNavigate={setCurrentPage} />
  }

  return <MainPage onLogout={() => { setAuthed(false); setCurrentPage('main') }} onNavigate={setCurrentPage} />
}
