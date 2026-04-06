import { useState, useEffect, useRef } from 'react'

function ipToLong(ip) {
  const parts = ip.trim().split('.')
  if (parts.length !== 4) return ''
  const nums = parts.map(Number)
  if (nums.some(n => isNaN(n) || n < 0 || n > 255)) return ''
  return String(((nums[0] * 256 + nums[1]) * 256 + nums[2]) * 256 + nums[3])
}

function longToIp(long) {
  const n = Number(long)
  if (isNaN(n) || n < 0 || n > 4294967295) return ''
  return [
    (n >>> 24) & 0xff,
    (n >>> 16) & 0xff,
    (n >>> 8) & 0xff,
    n & 0xff,
  ].join('.')
}

function dateToEpoch(str) {
  const ms = new Date(str).getTime()
  return isNaN(ms) ? '' : String(ms)
}

function epochToDate(str) {
  const ms = Number(str)
  if (isNaN(ms)) return ''
  return new Date(ms).toISOString()
}

export default function ConverterPanel() {
  const [ip, setIp] = useState('')
  const [ipLong, setIpLong] = useState('')
  const [dateStr, setDateStr] = useState('')
  const [epoch, setEpoch] = useState('')
  const [clock, setClock] = useState(new Date())
  const pausedRef = useRef(false)

  useEffect(() => {
    const id = setInterval(() => {
      if (!pausedRef.current) setClock(new Date())
    }, 1000)
    return () => clearInterval(id)
  }, [])

  return (
    <div style={{
      background: 'var(--color-surface)',
      border: '1px solid var(--color-border)',
      borderTop: 'none',
      padding: '10px 16px',
      display: 'flex',
      gap: 24,
      alignItems: 'flex-start',
      flexShrink: 0,
      flexWrap: 'wrap',
    }}>
      {/* IP ↔ Long */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--color-text-2)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>IP ↔ Long</span>
        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          <input
            value={ip}
            onChange={e => setIp(e.target.value)}
            onBlur={() => { if (ip) setIpLong(ipToLong(ip)) }}
            placeholder="192.168.1.1"
            style={inputStyle}
          />
          <span style={{ color: 'var(--color-text-3)', fontSize: 12 }}>↔</span>
          <input
            value={ipLong}
            onChange={e => setIpLong(e.target.value)}
            onBlur={() => { if (ipLong) setIp(longToIp(ipLong)) }}
            placeholder="3232235777"
            style={inputStyle}
          />
        </div>
      </div>

      {/* Date ↔ Epoch */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--color-text-2)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Date ↔ Epoch (ms)</span>
        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          <input
            value={dateStr}
            onChange={e => setDateStr(e.target.value)}
            onBlur={() => { if (dateStr) setEpoch(dateToEpoch(dateStr)) }}
            placeholder="2024-01-15T10:00:00Z"
            style={{ ...inputStyle, width: 180 }}
          />
          <span style={{ color: 'var(--color-text-3)', fontSize: 12 }}>↔</span>
          <input
            value={epoch}
            onChange={e => setEpoch(e.target.value)}
            onBlur={() => { if (epoch) setDateStr(epochToDate(epoch)) }}
            placeholder="1705312800000"
            style={inputStyle}
          />
        </div>
      </div>

      {/* Server clock */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--color-text-2)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Clock</span>
        <div
          onMouseEnter={() => { pausedRef.current = true }}
          onMouseLeave={() => { pausedRef.current = false }}
          style={{ fontFamily: 'var(--font-mono)', fontSize: 13, color: 'var(--color-text)', padding: '4px 8px', background: 'var(--color-surface-2)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-sm)', cursor: 'default', userSelect: 'text' }}
          title="Pauses on hover"
        >
          {clock.toISOString()}
        </div>
      </div>
    </div>
  )
}

const inputStyle = {
  width: 130,
  padding: '4px 8px',
  fontSize: 12,
  fontFamily: 'var(--font-mono)',
  border: '1px solid var(--color-border)',
  borderRadius: 'var(--radius-sm)',
  background: 'var(--color-surface-2)',
  color: 'var(--color-text)',
}
