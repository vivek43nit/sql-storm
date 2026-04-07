import { useState } from 'react'

// Normalize ISO 8601 datetimes (e.g. "2026-04-06T17:48:50.000+00:00") to
// MariaDB/MySQL format ("2026-04-06 17:48:50") which ps.setObject accepts.
function normalizeValue(val) {
  if (typeof val === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/.test(val)) {
    return val.replace('T', ' ').replace(/(\.\d+)?(Z|[+-]\d{2}:\d{2})?$/, '')
  }
  return val == null ? '' : String(val)
}

export default function RowModal({ mode, columns, row, pk, onSave, onDelete, onClose }) {
  const [formData, setFormData] = useState(() => {
    if (mode === 'edit' && row) {
      return Object.fromEntries(columns.map(col => [col, normalizeValue(row[col])]))
    }
    return Object.fromEntries(columns.map(col => [col, '']))
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setSaving(true)
    setError(null)
    try {
      await onSave(formData)
      onClose()
    } catch (err) {
      setError(err.response?.data || err.message || 'Error')
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    setSaving(true)
    setError(null)
    try {
      await onDelete()
      onClose()
    } catch (err) {
      setError(err.response?.data || err.message || 'Error')
      setSaving(false)
    }
  }

  return (
    <div
      onClick={(e) => { if (e.target === e.currentTarget) onClose() }}
      style={{
        position: 'fixed', inset: 0, zIndex: 1000,
        background: 'rgba(0,0,0,0.45)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}
    >
      <div style={{
        background: 'var(--color-surface)',
        border: '1px solid var(--color-border)',
        borderRadius: 'var(--radius-lg)',
        boxShadow: 'var(--shadow-md)',
        width: 480,
        maxWidth: '90vw',
        maxHeight: '80vh',
        display: 'flex',
        flexDirection: 'column',
      }}>
        {/* Header */}
        <div style={{
          padding: '12px 16px',
          borderBottom: '1px solid var(--color-border)',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        }}>
          <span style={{ fontWeight: 600, fontSize: 14, color: 'var(--color-text)' }}>
            {mode === 'add' ? 'Add Row' : mode === 'edit' ? 'Edit Row' : 'Delete Row'}
          </span>
          <button onClick={onClose} style={closeBtnStyle}>✕</button>
        </div>

        {mode === 'delete' ? (
          /* Delete confirmation */
          <div style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 16 }}>
            <p style={{ margin: 0, fontSize: 14, color: 'var(--color-text)' }}>
              Delete row where <code style={{ background: 'var(--color-surface-2)', padding: '1px 5px', borderRadius: 3, fontFamily: 'var(--font-mono)' }}>{pk} = {String(row?.[pk])}</code>?
            </p>
            {error && <div style={{ fontSize: 13, color: 'var(--color-error-text)' }}>{error}</div>}
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <button onClick={onClose} style={cancelBtnStyle}>Cancel</button>
              <button onClick={handleDelete} disabled={saving} style={deleteBtnStyle}>
                {saving ? 'Deleting…' : 'Delete'}
              </button>
            </div>
          </div>
        ) : (
          /* Add / Edit form */
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
            <div style={{ overflowY: 'auto', padding: '12px 16px', display: 'flex', flexDirection: 'column', gap: 8 }}>
              {columns.map(col => {
                const inputId = `row-field-${col.replace(/[^a-zA-Z0-9]/g, '-')}`;
                return (
                <div key={col} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <label htmlFor={inputId} style={{ width: 130, fontSize: 12, color: 'var(--color-text-2)', fontFamily: 'var(--font-mono)', flexShrink: 0, textAlign: 'right' }}>
                    {col}
                  </label>
                  <input
                    id={inputId}
                    value={formData[col] ?? ''}
                    onChange={e => setFormData(prev => ({ ...prev, [col]: e.target.value }))}
                    disabled={mode === 'edit' && col === pk}
                    placeholder={mode === 'edit' && col === pk ? '(primary key)' : ''}
                    style={{
                      flex: 1,
                      padding: '5px 8px',
                      fontSize: 13,
                      fontFamily: 'var(--font-mono)',
                      border: '1px solid var(--color-border)',
                      borderRadius: 'var(--radius-sm)',
                      background: mode === 'edit' && col === pk ? 'var(--color-surface-2)' : 'var(--color-surface)',
                      color: 'var(--color-text)',
                    }}
                  />
                </div>
              )})}

            </div>

            {error && (
              <div style={{ padding: '6px 16px', fontSize: 13, color: 'var(--color-error-text)', background: 'var(--color-error-bg)' }}>
                {typeof error === 'object' ? JSON.stringify(error) : error}
              </div>
            )}

            <div style={{ padding: '10px 16px', borderTop: '1px solid var(--color-border)', display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <button type="button" onClick={onClose} style={cancelBtnStyle}>Cancel</button>
              <button type="submit" disabled={saving} style={saveBtnStyle}>
                {saving ? 'Saving…' : mode === 'add' ? 'Add Row' : 'Save'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}

const closeBtnStyle = {
  background: 'transparent', border: 'none', cursor: 'pointer',
  fontSize: 14, color: 'var(--color-text-3)', padding: '2px 6px',
}

const cancelBtnStyle = {
  padding: '5px 14px', fontSize: 13,
  background: 'var(--color-surface-2)', color: 'var(--color-text-2)',
  border: '1px solid var(--color-border)', borderRadius: 'var(--radius-sm)', cursor: 'pointer',
}

const saveBtnStyle = {
  padding: '5px 14px', fontSize: 13,
  background: 'var(--color-primary)', color: '#fff',
  border: 'none', borderRadius: 'var(--radius-sm)', cursor: 'pointer',
}

const deleteBtnStyle = {
  padding: '5px 14px', fontSize: 13,
  background: '#dc2626', color: '#fff',
  border: 'none', borderRadius: 'var(--radius-sm)', cursor: 'pointer',
}
