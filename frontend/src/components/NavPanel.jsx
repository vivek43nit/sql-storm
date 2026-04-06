import { useState, useEffect } from 'react'
import { getGroups, getDatabases, getTables } from '../api/client'

const selectStyle = {
  width: '100%', padding: '6px 8px',
  background: '#1e293b', color: 'var(--color-sidebar-text)',
  border: '1px solid #334155', borderRadius: 'var(--radius-sm)',
  fontSize: 13,
}

export default function NavPanel({ onTableSelect }) {
  const [groups, setGroups] = useState([])
  const [databases, setDatabases] = useState([])
  const [tables, setTables] = useState([])
  const [selectedGroup, setSelectedGroup] = useState('')
  const [selectedDb, setSelectedDb] = useState('')
  const [activeTable, setActiveTable] = useState('')

  useEffect(() => {
    getGroups()
      .then(data => setGroups(Array.isArray(data) ? data : Array.from(data)))
      .catch(console.error)
  }, [])

  const handleGroupChange = async (group) => {
    setSelectedGroup(group)
    setSelectedDb('')
    setTables([])
    setDatabases([])
    setActiveTable('')
    if (!group) return
    try {
      const data = await getDatabases(group)
      setDatabases(Array.isArray(data) ? data : Array.from(data))
    } catch (err) { console.error(err) }
  }

  const handleDbChange = async (db) => {
    setSelectedDb(db)
    setTables([])
    setActiveTable('')
    if (!db || !selectedGroup) return
    try {
      const data = await getTables(selectedGroup, db)
      setTables(Array.isArray(data) ? data : [])
    } catch (err) { console.error(err) }
  }

  const handleTableClick = (t) => {
    setActiveTable(t.name)
    onTableSelect({ group: selectedGroup, database: selectedDb, table: t.name, primaryKey: t.primaryKey })
  }

  return (
    <div style={{
      width: 220, background: 'var(--color-sidebar-bg)', color: 'var(--color-sidebar-text)',
      display: 'flex', flexDirection: 'column', flexShrink: 0, overflow: 'hidden',
      borderRight: '1px solid #1e293b',
    }}>
      {/* Brand */}
      <div style={{
        padding: '14px 16px', borderBottom: '1px solid #1e293b',
        fontWeight: 700, fontSize: 15, letterSpacing: '-0.2px', display: 'flex', alignItems: 'center', gap: 8,
      }}>
        <span>⚡</span> SQL Storm
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: '12px 12px 20px' }}>
        {/* Group */}
        <div style={{ marginBottom: 14 }}>
          <label style={{ display: 'block', fontSize: 10, fontWeight: 600, letterSpacing: '0.08em', color: 'var(--color-sidebar-muted)', textTransform: 'uppercase', marginBottom: 6 }}>
            Group
          </label>
          <select style={selectStyle} value={selectedGroup} onChange={e => handleGroupChange(e.target.value)}>
            <option value="">— select —</option>
            {groups.map(g => <option key={g} value={g}>{g}</option>)}
          </select>
        </div>

        {/* Database */}
        <div style={{ marginBottom: 16 }}>
          <label style={{ display: 'block', fontSize: 10, fontWeight: 600, letterSpacing: '0.08em', color: 'var(--color-sidebar-muted)', textTransform: 'uppercase', marginBottom: 6 }}>
            Database
          </label>
          <select style={{ ...selectStyle, opacity: !selectedGroup ? 0.45 : 1 }} value={selectedDb} onChange={e => handleDbChange(e.target.value)} disabled={!selectedGroup}>
            <option value="">— select —</option>
            {databases.map(d => <option key={d} value={d}>{d}</option>)}
          </select>
        </div>

        {/* Tables */}
        {selectedDb && (
          <>
            <label style={{ display: 'block', fontSize: 10, fontWeight: 600, letterSpacing: '0.08em', color: 'var(--color-sidebar-muted)', textTransform: 'uppercase', marginBottom: 6 }}>
              Tables
            </label>
            {tables.length === 0
              ? <div style={{ fontSize: 12, color: 'var(--color-sidebar-muted)', padding: '4px 0' }}>No tables found</div>
              : tables.map(t => (
                <div
                  key={t.name}
                  onClick={() => handleTableClick(t)}
                  title={t.remark || t.name}
                  style={{
                    padding: '6px 8px', borderRadius: 'var(--radius-sm)', cursor: 'pointer',
                    fontSize: 13, userSelect: 'none', marginBottom: 1,
                    background: activeTable === t.name ? 'var(--color-sidebar-active)' : 'transparent',
                    color: activeTable === t.name ? '#fff' : 'var(--color-sidebar-text)',
                    transition: 'background 0.1s',
                  }}
                  onMouseEnter={e => { if (activeTable !== t.name) e.currentTarget.style.background = 'var(--color-sidebar-hover)' }}
                  onMouseLeave={e => { if (activeTable !== t.name) e.currentTarget.style.background = 'transparent' }}
                >
                  {t.name}
                </div>
              ))
            }
          </>
        )}
      </div>
    </div>
  )
}
