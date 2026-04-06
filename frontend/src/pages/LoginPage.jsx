import { useState } from 'react'
import { login } from '../api/client'

export default function LoginPage({ onLogin }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await login(username, password)
      onLogin()
    } catch (err) {
      setError(err.response?.data?.error || 'Invalid credentials')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      height: '100%', background: 'var(--color-sidebar-bg)',
    }}>
      <div style={{
        background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)',
        padding: '40px 36px', width: 360, boxShadow: 'var(--shadow-md)',
      }}>
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <div style={{ fontSize: 28, marginBottom: 8 }}>⚡</div>
          <div style={{ fontSize: 20, fontWeight: 700, color: 'var(--color-text)', letterSpacing: '-0.3px' }}>
            SQL Storm
          </div>
          <div style={{ fontSize: 13, color: 'var(--color-text-3)', marginTop: 4 }}>
            Sign in to continue
          </div>
        </div>

        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: 16 }}>
            <label style={{ display: 'block', marginBottom: 6, fontWeight: 500, fontSize: 13, color: 'var(--color-text-2)' }}>
              Username
            </label>
            <input
              type="text"
              value={username}
              onChange={e => setUsername(e.target.value)}
              autoFocus
              required
              style={{ width: '100%', padding: '8px 10px' }}
            />
          </div>

          <div style={{ marginBottom: 24 }}>
            <label style={{ display: 'block', marginBottom: 6, fontWeight: 500, fontSize: 13, color: 'var(--color-text-2)' }}>
              Password
            </label>
            <input
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              required
              style={{ width: '100%', padding: '8px 10px' }}
            />
          </div>

          {error && (
            <div style={{
              marginBottom: 16, padding: '8px 12px',
              background: 'var(--color-error-bg)', color: 'var(--color-error-text)',
              borderRadius: 'var(--radius-sm)', fontSize: 13,
            }}>
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            style={{
              width: '100%', padding: '10px',
              background: 'var(--color-primary)', color: '#fff',
              fontWeight: 600, fontSize: 14, borderRadius: 'var(--radius-md)',
            }}
          >
            {loading ? 'Signing in…' : 'Sign In'}
          </button>
        </form>
      </div>
    </div>
  )
}
