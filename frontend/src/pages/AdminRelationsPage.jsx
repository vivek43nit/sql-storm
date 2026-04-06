import { useState, useEffect } from 'react'
import { getGroups, getDatabases, getTables, getAdminRelations } from '../api/client'

export default function AdminRelationsPage({ onNavigate }) {
  const [groups, setGroups] = useState([])
  const [databases, setDatabases] = useState([])
  const [tables, setTables] = useState([])
  const [group, setGroup] = useState('')
  const [database, setDatabase] = useState('')
  const [table, setTable] = useState('')
  const [relations, setRelations] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    getGroups().then(setGroups).catch(() => {})
  }, [])

  useEffect(() => {
    setDatabase('')
    setTables([])
    setTable('')
    setRelations(null)
    if (group) getDatabases(group).then(setDatabases).catch(() => {})
    else setDatabases([])
  }, [group])

  useEffect(() => {
    setTable('')
    setRelations(null)
    if (group && database) getTables(group, database).then(setTables).catch(() => {})
    else setTables([])
  }, [database])

  useEffect(() => {
    if (!group || !database || !table) { setRelations(null); return }
    setLoading(true)
    setError(null)
    getAdminRelations(group, database, table)
      .then(data => { setRelations(data); setLoading(false) })
      .catch(err => { setError(err.response?.data || err.message); setLoading(false) })
  }, [table])

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: 'var(--color-bg)' }}>
      {/* Header */}
      <div style={{
        background: 'var(--color-header-bg)', color: '#e2e8f0',
        padding: '0 16px', height: 44,
        display: 'flex', alignItems: 'center', gap: 12, flexShrink: 0,
      }}>
        <button onClick={() => onNavigate('main')} style={backBtnStyle}>← Back</button>
        <span style={{ fontWeight: 600, fontSize: 14 }}>Admin — Relations</span>
      </div>

      <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12, overflow: 'auto' }}>
        {/* Selectors */}
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <select value={group} onChange={e => setGroup(e.target.value)} style={selectStyle}>
            <option value="">— Group —</option>
            {groups.map(g => <option key={g} value={g}>{g}</option>)}
          </select>
          <select value={database} onChange={e => setDatabase(e.target.value)} disabled={!group} style={selectStyle}>
            <option value="">— Database —</option>
            {databases.map(d => <option key={d} value={d}>{d}</option>)}
          </select>
          <select value={table} onChange={e => setTable(e.target.value)} disabled={!database} style={selectStyle}>
            <option value="">— Table —</option>
            {tables.map(t => <option key={t.name} value={t.name}>{t.name}</option>)}
          </select>
        </div>

        {error && (
          <div style={{ padding: '8px 12px', background: 'var(--color-error-bg)', color: 'var(--color-error-text)', borderRadius: 'var(--radius-sm)', fontSize: 13 }}>
            {typeof error === 'object' ? JSON.stringify(error) : error}
          </div>
        )}

        {loading && <div style={{ color: 'var(--color-text-3)', fontSize: 13 }}>Loading…</div>}

        {relations && !loading && (
          <div style={{
            background: 'var(--color-surface)',
            border: '1px solid var(--color-border)',
            borderRadius: 'var(--radius-md)',
            overflow: 'hidden',
          }}>
            <table style={{ borderCollapse: 'collapse', width: '100%', fontSize: 13 }}>
              <thead>
                <tr>
                  {['Table', 'Column', 'Refer To', 'Referenced By'].map(h => (
                    <th key={h} style={thStyle}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {relations.length === 0 ? (
                  <tr><td colSpan={4} style={{ ...tdStyle, color: 'var(--color-text-3)', textAlign: 'center' }}>No relations found.</td></tr>
                ) : (
                  relations.map((rel, i) => (
                    <tr key={i} style={{ background: i % 2 === 0 ? 'var(--color-surface)' : 'var(--color-surface-2)' }}>
                      <td style={tdStyle}>{rel.table}</td>
                      <td style={{ ...tdStyle, fontFamily: 'var(--font-mono)' }}>{rel.column}</td>
                      <td style={{ ...tdStyle, fontFamily: 'var(--font-mono)' }}>{rel.referTo?.join(', ') || '—'}</td>
                      <td style={{ ...tdStyle, fontFamily: 'var(--font-mono)' }}>{rel.referencedBy?.join(', ') || '—'}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}

        {!group && !loading && (
          <div style={{ color: 'var(--color-text-3)', fontSize: 14, textAlign: 'center', marginTop: 40 }}>
            Select a group, database, and table to view FK relations.
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
}

const thStyle = {
  padding: '8px 12px',
  background: 'var(--color-surface-2)',
  borderBottom: '2px solid var(--color-border)',
  borderRight: '1px solid var(--color-border)',
  textAlign: 'left', fontSize: 12, fontWeight: 600,
  color: 'var(--color-text-2)',
}

const tdStyle = {
  padding: '6px 12px',
  borderBottom: '1px solid var(--color-border)',
  borderRight: '1px solid var(--color-border)',
  color: 'var(--color-text)',
}
