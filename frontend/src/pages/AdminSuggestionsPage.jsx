import { useState, useEffect } from 'react'
import { getGroups, getAdminSuggestions } from '../api/client'

export default function AdminSuggestionsPage({ onNavigate }) {
  const [groups, setGroups] = useState([])
  const [group, setGroup] = useState('')
  const [suggestions, setSuggestions] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    getGroups().then(setGroups).catch(() => {})
  }, [])

  useEffect(() => {
    if (!group) { setSuggestions(null); return }
    setLoading(true)
    setError(null)
    getAdminSuggestions(group)
      .then(data => { setSuggestions(data); setLoading(false) })
      .catch(err => { setError(err.response?.data || err.message); setLoading(false) })
  }, [group])

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: 'var(--color-bg)' }}>
      {/* Header */}
      <div style={{
        background: 'var(--color-header-bg)', color: '#e2e8f0',
        padding: '0 16px', height: 44,
        display: 'flex', alignItems: 'center', gap: 12, flexShrink: 0,
      }}>
        <button onClick={() => onNavigate('main')} style={backBtnStyle}>← Back</button>
        <span style={{ fontWeight: 600, fontSize: 14 }}>Admin — Suggestions</span>
      </div>

      <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12, overflow: 'auto' }}>
        <select value={group} onChange={e => setGroup(e.target.value)} style={selectStyle}>
          <option value="">— Select Group —</option>
          {groups.map(g => <option key={g} value={g}>{g}</option>)}
        </select>

        {error && (
          <div style={{ padding: '8px 12px', background: 'var(--color-error-bg)', color: 'var(--color-error-text)', borderRadius: 'var(--radius-sm)', fontSize: 13 }}>
            {typeof error === 'object' ? JSON.stringify(error) : error}
          </div>
        )}

        {loading && <div style={{ color: 'var(--color-text-3)', fontSize: 13 }}>Loading…</div>}

        {suggestions && !loading && (
          Object.keys(suggestions).length === 0 ? (
            <div style={{ color: 'var(--color-text-3)', fontSize: 14, textAlign: 'center', marginTop: 20 }}>
              No FK suggestions found for this group.
            </div>
          ) : (
            Object.entries(suggestions).map(([db, dbSuggestions]) => (
              <div key={db} style={{
                background: 'var(--color-surface)',
                border: '1px solid var(--color-border)',
                borderRadius: 'var(--radius-md)',
                overflow: 'hidden',
              }}>
                <div style={{
                  padding: '8px 12px',
                  background: 'var(--color-surface-2)',
                  borderBottom: '1px solid var(--color-border)',
                  fontWeight: 600, fontSize: 13, color: 'var(--color-text)',
                }}>
                  {db}
                </div>
                <div style={{ padding: '8px 12px', display: 'flex', flexDirection: 'column', gap: 4 }}>
                  {Array.isArray(dbSuggestions) && dbSuggestions.length === 0 && (
                    <span style={{ fontSize: 13, color: 'var(--color-text-3)' }}>No suggestions.</span>
                  )}
                  {Array.isArray(dbSuggestions) && dbSuggestions.map((s, i) => (
                    <div key={i} style={{ fontSize: 13, fontFamily: 'var(--font-mono)', color: 'var(--color-text)', padding: '3px 0', borderBottom: i < dbSuggestions.length - 1 ? '1px solid var(--color-border)' : 'none' }}>
                      {typeof s === 'string' ? s : JSON.stringify(s)}
                    </div>
                  ))}
                  {!Array.isArray(dbSuggestions) && (
                    <pre style={{ fontSize: 12, color: 'var(--color-text)', margin: 0, fontFamily: 'var(--font-mono)' }}>
                      {JSON.stringify(dbSuggestions, null, 2)}
                    </pre>
                  )}
                </div>
              </div>
            ))
          )
        )}

        {!group && !loading && (
          <div style={{ color: 'var(--color-text-3)', fontSize: 14, textAlign: 'center', marginTop: 40 }}>
            Select a group to view FK suggestions.
          </div>
        )}
      </div>
    </div>
  )
}

const backBtnStyle = {
  background: 'transparent', border: '1px solid #334155',
  color: '#94a3b8', padding: '4px 10px',
  borderRadius: 'var(--radius-sm)', fontSize: 12, cursor: 'pointer',
}

const selectStyle = {
  padding: '5px 8px', fontSize: 13,
  border: '1px solid var(--color-border)',
  borderRadius: 'var(--radius-sm)',
  background: 'var(--color-surface)',
  color: 'var(--color-text)',
  width: 200,
}
