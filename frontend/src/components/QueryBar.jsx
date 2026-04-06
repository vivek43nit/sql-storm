import { useState } from 'react'

export default function QueryBar({ context, onExecute }) {
  const [query, setQuery] = useState('')

  const handleRun = () => {
    if (query.trim() && context) onExecute(query.trim())
  }

  return (
    <div style={{
      display: 'flex', gap: 8, padding: '8px 12px',
      background: 'var(--color-surface)', borderBottom: '1px solid var(--color-border)',
    }}>
      <input
        style={{
          flex: 1, fontFamily: 'var(--font-mono)', fontSize: 13,
          padding: '6px 10px', background: 'var(--color-surface-2)',
        }}
        value={query}
        onChange={e => setQuery(e.target.value)}
        onKeyDown={e => e.key === 'Enter' && handleRun()}
        placeholder={context ? `SELECT * FROM ${context.table} LIMIT 100` : 'Select a table first…'}
        disabled={!context}
      />
      <button
        onClick={handleRun}
        disabled={!context || !query.trim()}
        style={{
          padding: '6px 18px', background: 'var(--color-primary)', color: '#fff',
          fontWeight: 600, borderRadius: 'var(--radius-sm)',
        }}
      >
        Run
      </button>
    </div>
  )
}
