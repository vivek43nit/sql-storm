import { useState, useEffect } from 'react'
import LoginPage from './pages/LoginPage'
import MainPage from './pages/MainPage'
import { getGroups } from './api/client'

export default function App() {
  const [authed, setAuthed] = useState(null)  // null = checking, false = not authed, true = authed

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

  return <MainPage onLogout={() => setAuthed(false)} />
}
