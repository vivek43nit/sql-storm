import { useState } from 'react'
import NavPanel from '../components/NavPanel'
import QueryBar from '../components/QueryBar'
import TableGrid from '../components/TableGrid'
import { executeQuery, logout } from '../api/client'

export default function MainPage({ onLogout }) {
  const [context, setContext] = useState(null)
  const [resultSets, setResultSets] = useState([])
  const [error, setError] = useState(null)

  const handleTableSelect = async (ctx) => {
    setContext(ctx)
    setError(null)
    try {
      const query = `SELECT * FROM \`${ctx.table}\` LIMIT 100`
      const result = await executeQuery(ctx.group, query, ctx.database, 'S', ctx.table, 'self')
      setResultSets([result])
    } catch (err) {
      setError(err.response?.data || err.message)
    }
  }

  const handleQueryRun = async (query) => {
    if (!context) return
    setError(null)
    try {
      const result = await executeQuery(context.group, query, context.database, 'S', query, 'self')
      setResultSets([result])
    } catch (err) {
      setError(err.response?.data || err.message)
    }
  }

  const handleAddResults = (newResults) => {
    setResultSets(newResults)
  }

  const buildQuery = (table, filters, sort, limit = 100) => {
    const escape = v => String(v).replace(/'/g, "''")
    let q = `SELECT * FROM \`${table}\``
    const conditions = Object.entries(filters).filter(([, v]) => v?.trim())
    if (conditions.length > 0)
      q += ' WHERE ' + conditions.map(([col, val]) => `\`${col}\` LIKE '%${escape(val)}%'`).join(' AND ')
    if (sort?.col)
      q += ` ORDER BY \`${sort.col}\` ${sort.dir}`
    q += ` LIMIT ${limit}`
    return q
  }

  const handleReQuery = (index, table, database, filters, sort) => {
    const query = buildQuery(table, filters, sort)
    executeQuery(context.group, query, database, 'S', table, 'self')
      .then(result => setResultSets(prev => prev.map((rs, i) => i === index ? result : rs)))
      .catch(err => setError(err.response?.data || err.message))
  }

  const handleLogout = async () => {
    try { await logout() } catch {}
    onLogout()
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* Top bar */}
      <div style={{
        background: 'var(--color-header-bg)', color: '#e2e8f0',
        padding: '0 16px', height: 44,
        display: 'flex', alignItems: 'center', gap: 12, flexShrink: 0,
      }}>
        {context && (
          <span style={{ fontSize: 12, color: '#64748b' }}>
            {context.group}
            <span style={{ margin: '0 5px', color: '#334155' }}>/</span>
            {context.database}
            <span style={{ margin: '0 5px', color: '#334155' }}>/</span>
            <span style={{ color: '#94a3b8', fontWeight: 600 }}>{context.table}</span>
          </span>
        )}
        <button
          onClick={handleLogout}
          style={{
            marginLeft: 'auto', background: 'transparent',
            border: '1px solid #334155', color: '#64748b',
            padding: '4px 12px', borderRadius: 'var(--radius-sm)', fontSize: 12,
          }}
        >
          Logout
        </button>
      </div>

      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        <NavPanel onTableSelect={handleTableSelect} />

        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', background: 'var(--color-bg)' }}>
          <QueryBar context={context} onExecute={handleQueryRun} />

          {error && (
            <div style={{
              padding: '8px 14px', background: 'var(--color-error-bg)',
              color: 'var(--color-error-text)', borderBottom: '1px solid #fecaca', fontSize: 13,
            }}>
              {typeof error === 'object' ? JSON.stringify(error) : error}
            </div>
          )}

          <div style={{ flex: 1, overflowY: 'auto', padding: '12px 14px' }}>
            {resultSets.length === 0 && !error && (
              <div style={{ color: 'var(--color-text-3)', marginTop: 60, textAlign: 'center', fontSize: 14 }}>
                Select a table from the sidebar to get started.
              </div>
            )}
            {resultSets.map((rs, i) => (
              <TableGrid
                key={i}
                resultSet={rs}
                group={context?.group}
                onAddResults={handleAddResults}
                onReQuery={(filters, sort) => handleReQuery(i, rs.table, rs.database, filters, sort)}
              />
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
