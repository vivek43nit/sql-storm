import {
  useReactTable,
  getCoreRowModel,
  getSortedRowModel,
  flexRender,
} from '@tanstack/react-table'
import { useMemo, useState, useEffect, useRef } from 'react'
import { getReferences, getDeReferences, traceRow, addRow, editRow, deleteRow } from '../api/client'
import RowModal from './RowModal'

export default function TableGrid({ resultSet, group, onAddResults, onReQuery, refRowLimit = 100, userRole }) {
  const canMutate = userRole === 'ADMIN' || userRole === 'READ_WRITE'
  const isSelfRelation = !resultSet?.relation || resultSet.relation === 'self'

  // Server-side filter/sort state (only for self-relation tables)
  const [filters, setFilters] = useState({})   // { colName: value }
  const [sort, setSort] = useState(null)         // { col, dir: 'ASC'|'DESC' } | null

  // Client-side sort for FK result sets
  const [sorting, setSorting] = useState([])

  const [loading, setLoading] = useState(null)
  const [modal, setModal] = useState(null)  // { mode: 'add'|'edit'|'delete', row: object|null }
  const isFirstRender = useRef(true)

  const referTo = resultSet?.referToColumns ?? {}
  const referencedBy = resultSet?.referencedByColumns ?? {}
  const pk = resultSet?.pk || null
  const canEdit = canMutate && isSelfRelation && pk && resultSet?.updatable
  const canDelete = canMutate && isSelfRelation && pk && resultSet?.deletable

  const relationCol = useMemo(() => {
    if (isSelfRelation) return null
    const info = resultSet?.info || ''
    const lastPath = info.split(' -> ').pop() ?? ''
    return lastPath.split('.').pop() || null
  }, [resultSet?.relation, resultSet?.info])

  // Debounced server-side re-query when filters or sort change
  useEffect(() => {
    if (isFirstRender.current) { isFirstRender.current = false; return }
    if (!isSelfRelation) return
    const timeout = setTimeout(() => onReQuery?.(filters, sort), 350)
    return () => clearTimeout(timeout)
  }, [filters, sort])

  const handleSortClick = (col) => {
    if (!isSelfRelation) return  // handled by TanStack for FK results
    setSort(prev => {
      if (prev?.col === col) {
        if (prev.dir === 'ASC') return { col, dir: 'DESC' }
        return null  // third click clears sort
      }
      return { col, dir: 'ASC' }
    })
  }

  const handleSave = async (formData) => {
    const db = resultSet.database
    const tbl = resultSet.table
    if (modal.mode === 'add') {
      await addRow(group, db, tbl, formData)
    } else {
      await editRow(group, db, tbl, pk, modal.row[pk], formData)
    }
    onReQuery?.(filters, sort)
  }

  const handleDelete = async () => {
    const db = resultSet.database
    const tbl = resultSet.table
    await deleteRow(group, db, tbl, pk, modal.row[pk])
    onReQuery?.(filters, sort)
  }

  const columns = useMemo(() => {
    if (!resultSet?.columns?.length) return []
    return resultSet.columns.map(col => ({
      accessorKey: col,
      header: () => {
        const isRelationCol = col === relationCol
        return (
          <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <span style={isRelationCol ? { color: 'var(--color-text)', fontWeight: 700 } : {}}>
              {col}
            </span>
            {referTo[col] && (
              <span title={`→ ${referTo[col].join(', ')}`} style={{ color: 'var(--color-fk-refer)', fontSize: 11 }}>↗</span>
            )}
            {referencedBy[col] && (
              <span title={`← ${referencedBy[col].join(', ')}`} style={{ color: 'var(--color-fk-refby)', fontSize: 11 }}>↙</span>
            )}
          </span>
        )
      },
      cell: info => {
        const val = info.getValue()
        if (val === null || val === undefined)
          return <span style={{ color: 'var(--color-text-3)', fontStyle: 'italic' }}>null</span>
        return String(val)
      }
    }))
  }, [resultSet?.columns, referTo, referencedBy, relationCol])

  const table = useReactTable({
    data: resultSet?.rows ?? [],
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  })

  const navigate = async (key, fn) => {
    setLoading(key)
    try {
      const results = await fn()
      if (results?.length > 0) onAddResults?.(results)
    } catch (err) {
      console.error(err)
    } finally {
      setLoading(null)
    }
  }

  if (!resultSet) return null

  const db = resultSet.database
  const tbl = resultSet.table
  const hasFK = Object.keys(referTo).length > 0 || Object.keys(referencedBy).length > 0
  const isSelf = resultSet.relation === 'self' && resultSet.info === 'SELF'
  const isReferTo = resultSet.relation === 'referTo'
  const isRefBy = resultSet.relation && resultSet.relation !== 'self' && !isReferTo

  const relationChipLabel = useMemo(() => {
    const info = resultSet.info || ''
    const stripDb = p => p.split('.').slice(-2).join('.')
    if (info.includes(' -> ')) {
      const [from, to] = info.split(' -> ')
      if (isRefBy) return `${stripDb(to)} → ${stripDb(from)}`
      return `${stripDb(from)} → ${stripDb(to)}`
    }
    return info
  }, [resultSet.info, isRefBy])

  const getSortIcon = (col) => {
    if (!isSelfRelation) {
      const s = sorting.find(s => s.id === col)
      if (!s) return null
      return <span style={{ color: 'var(--color-primary)' }}>{s.desc ? '▼' : '▲'}</span>
    }
    if (sort?.col !== col) return null
    return <span style={{ color: 'var(--color-primary)' }}>{sort.dir === 'ASC' ? '▲' : '▼'}</span>
  }

  return (
    <>
      {modal && (
        <RowModal
          mode={modal.mode}
          columns={resultSet.columns ?? []}
          row={modal.row}
          pk={pk}
          onSave={handleSave}
          onDelete={handleDelete}
          onClose={() => setModal(null)}
        />
      )}

      <div style={{
        marginBottom: 16,
        background: 'var(--color-surface)',
        border: '1px solid var(--color-border)',
        borderRadius: 'var(--radius-md)',
        overflow: 'hidden',
        boxShadow: 'var(--shadow-sm)',
      }}>
        {/* Header */}
        <div style={{
          padding: '7px 12px', background: 'var(--color-surface-2)',
          borderBottom: '1px solid var(--color-border)',
          display: 'flex', alignItems: 'center', gap: 8,
        }}>
          <span style={{ fontSize: 13 }}>
            <span style={{ color: 'var(--color-text-3)' }}>{db}.</span>
            <span style={{ fontWeight: 700, color: 'var(--color-text)' }}>{tbl}</span>
          </span>

          <span style={{ color: 'var(--color-border-2)' }}>·</span>
          <span style={{ color: 'var(--color-text-2)', fontSize: 12 }}>{resultSet.rows?.length ?? 0} rows</span>

          {isSelf && (
            <span style={{ background: '#f1f5f9', color: '#475569', border: '1px solid #cbd5e1', padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 500 }}>
              self
            </span>
          )}
          {isReferTo && (
            <span style={{ background: 'var(--color-fk-refer-bg)', color: 'var(--color-fk-refer)', border: '1px solid #bfdbfe', padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 500, fontFamily: 'var(--font-mono)' }}>
              {relationChipLabel}
            </span>
          )}
          {isRefBy && (
            <span style={{ background: 'var(--color-fk-refby-bg)', color: 'var(--color-fk-refby)', border: '1px solid #fed7aa', padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 500, fontFamily: 'var(--font-mono)' }}>
              {relationChipLabel}
            </span>
          )}

          {canEdit && (
            <button
              onClick={() => setModal({ mode: 'add', row: null })}
              style={{ marginLeft: 'auto', fontSize: 11, padding: '2px 10px', background: 'var(--color-primary)', color: '#fff', border: 'none', borderRadius: 'var(--radius-sm)', cursor: 'pointer' }}
            >
              + Add Row
            </button>
          )}
        </div>

        {/* FK legend */}
        {hasFK && (
          <div style={{ padding: '4px 12px', background: '#fafbfc', borderBottom: '1px solid var(--color-border)', fontSize: 11, color: 'var(--color-text-3)', display: 'flex', gap: 14 }}>
            {Object.keys(referTo).length > 0 && <span><span style={{ color: 'var(--color-fk-refer)' }}>↗</span> click value to follow FK</span>}
            {Object.keys(referencedBy).length > 0 && <span><span style={{ color: 'var(--color-fk-refby)' }}>↙</span> click to show referencing rows</span>}
          </div>
        )}

        <div style={{ overflowX: 'auto' }}>
          <table style={{ borderCollapse: 'collapse', fontSize: 13, width: '100%', tableLayout: 'auto' }}>
            <thead>
              {table.getHeaderGroups().map(hg => (
                <>
                  {/* Sort row */}
                  <tr key={hg.id}>
                    <th style={thStyle}>#</th>
                    {hg.headers.map(h => {
                      const isRelCol = h.column.id === relationCol
                      const sortHandler = isSelfRelation
                        ? () => handleSortClick(h.column.id)
                        : h.column.getToggleSortingHandler()
                      return (
                        <th key={h.id} style={{ ...thStyle, cursor: 'pointer', ...(isRelCol ? { background: 'var(--color-primary-bg)', borderBottom: '2px solid var(--color-primary)' } : {}) }} onClick={sortHandler}>
                          <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                            {flexRender(h.column.columnDef.header, h.getContext())}
                            {getSortIcon(h.column.id)}
                          </span>
                        </th>
                      )
                    })}
                    <th style={{ ...thStyle, width: 120 }}>Actions</th>
                  </tr>

                  {/* Filter row — server-side, self-relation only */}
                  {isSelfRelation && (
                    <tr key={hg.id + '-filter'}>
                      <th style={filterThStyle} aria-hidden="true" />
                      {hg.headers.map(h => (
                        <th key={h.id + '-f'} style={filterThStyle}>
                          <input
                            value={filters[h.column.id] ?? ''}
                            onChange={e => setFilters(prev => ({ ...prev, [h.column.id]: e.target.value }))}
                            placeholder="filter…"
                            aria-label={`Filter ${h.column.id}`}
                            style={{ width: '100%', padding: '2px 5px', fontSize: 11, border: '1px solid var(--color-border-2)', borderRadius: 3, background: 'var(--color-surface)', color: 'var(--color-text)', fontFamily: 'var(--font-mono)' }}
                          />
                        </th>
                      ))}
                      <th style={filterThStyle} aria-hidden="true" />
                    </tr>
                  )}
                </>
              ))}
            </thead>
            <tbody>
              {table.getRowModel().rows.map((row, idx) => (
                <tr key={row.id} style={{ background: idx % 2 === 0 ? 'var(--color-surface)' : 'var(--color-surface-2)' }}>
                  <td style={{ ...tdStyle, color: 'var(--color-text-3)', fontSize: 11, width: 36 }}>{idx + 1}</td>

                  {row.getVisibleCells().map(cell => {
                    const col = cell.column.id
                    const hasReferTo = !!referTo[col]
                    const hasRefBy = !!referencedBy[col]
                    const isRelCol = col === relationCol
                    const val = cell.getValue()
                    const loadKey = `referTo-${idx}-${col}`
                    const loadKeyRef = `refBy-${idx}-${col}`
                    const isNull = val === null || val === undefined
                    const display = isNull
                      ? <span style={{ color: 'var(--color-text-3)', fontStyle: 'italic' }}>null</span>
                      : String(val)

                    return (
                      <td key={cell.id} style={{ ...tdStyle, ...(isRelCol ? { background: 'var(--color-primary-bg)', fontWeight: 600 } : {}) }}>
                        {hasReferTo ? (
                          <span
                            onClick={() => navigate(loadKey, () => getDeReferences(group, db, tbl, col, row.original, refRowLimit))}
                            title={`Follow FK → ${referTo[col].join(', ')}`}
                            style={{ cursor: 'pointer', color: loading === loadKey ? 'var(--color-text-3)' : 'var(--color-fk-refer)', textDecoration: 'underline', textDecorationStyle: 'dotted', textDecorationColor: 'var(--color-fk-refer)' }}
                          >
                            {display}
                          </span>
                        ) : (
                          <span>{display}</span>
                        )}

                        {hasRefBy && !isNull && (
                          <button
                            onClick={() => navigate(loadKeyRef, () => getReferences(group, db, tbl, col, row.original, false, refRowLimit))}
                            title={`Show rows referencing this ← ${referencedBy[col].join(', ')}`}
                            style={{ marginLeft: 6, fontSize: 10, padding: '1px 5px', lineHeight: '14px', background: loading === loadKeyRef ? 'var(--color-border)' : 'var(--color-fk-refby-bg)', color: 'var(--color-fk-refby)', border: '1px solid #fed7aa', borderRadius: 3 }}
                          >
                            ↙
                          </button>
                        )}
                      </td>
                    )
                  })}

                  <td style={{ ...tdStyle, width: 120 }}>
                    <div style={{ display: 'flex', gap: 4 }}>
                      <button
                        onClick={() => navigate(`trace-${idx}`, () => traceRow(group, db, tbl, row.original, refRowLimit))}
                        disabled={loading === `trace-${idx}`}
                        title="Trace all FK relationships"
                        style={{ fontSize: 11, padding: '2px 7px', background: 'var(--color-surface-2)', color: 'var(--color-text-2)', border: '1px solid var(--color-border-2)', borderRadius: 3 }}
                      >
                        {loading === `trace-${idx}` ? '…' : 'Trace'}
                      </button>
                      {canEdit && (
                        <button
                          onClick={() => setModal({ mode: 'edit', row: row.original })}
                          title="Edit row"
                          style={{ fontSize: 11, padding: '2px 7px', background: '#eff6ff', color: '#2563eb', border: '1px solid #bfdbfe', borderRadius: 3 }}
                        >
                          Edit
                        </button>
                      )}
                      {canDelete && (
                        <button
                          onClick={() => setModal({ mode: 'delete', row: row.original })}
                          title="Delete row"
                          style={{ fontSize: 11, padding: '2px 7px', background: '#fef2f2', color: '#dc2626', border: '1px solid #fecaca', borderRadius: 3 }}
                        >
                          Del
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </>
  )
}

const filterThStyle = {
  padding: '3px 6px',
  background: 'var(--color-surface)',
  borderBottom: '1px solid var(--color-border)',
  borderRight: '1px solid var(--color-border)',
}

const thStyle = {
  padding: '7px 10px',
  background: 'var(--color-surface-2)',
  borderBottom: '2px solid var(--color-border)',
  borderRight: '1px solid var(--color-border)',
  textAlign: 'left', whiteSpace: 'nowrap',
  fontWeight: 600, fontSize: 12, color: 'var(--color-text-2)',
  userSelect: 'none',
}

const tdStyle = {
  padding: '5px 10px',
  borderBottom: '1px solid var(--color-border)',
  borderRight: '1px solid var(--color-border)',
  whiteSpace: 'nowrap', maxWidth: 300,
  overflow: 'hidden', textOverflow: 'ellipsis',
  verticalAlign: 'middle', color: 'var(--color-text)',
}
