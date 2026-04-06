import axios from 'axios'

const BASE = '/sql-storm/api'

const client = axios.create({
  baseURL: BASE,
  withCredentials: true,
})

export const login = (username, password) => {
  const params = new URLSearchParams()
  params.append('username', username)
  params.append('password', password)
  return client.post('/login', params, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
  }).then(r => r.data)
}

export const logout = () => client.post('/logout').then(r => r.data)

export const getGroups = () => client.get('/groups').then(r => r.data)

export const getDatabases = (group) =>
  client.get('/databases', { params: { group } }).then(r => r.data)

export const getTables = (group, database) =>
  client.get('/tables', { params: { group, database } }).then(r => r.data)

export const executeQuery = (group, query, database, queryType = 'S', info = '', relation = 'self') =>
  client.post('/execute', { query, database, queryType, info, relation }, { params: { group } }).then(r => r.data)

export const getReferences = (group, database, table, column, row, includeSelf = false, refRowLimit = 100) =>
  client.get('/references', {
    params: { group, database, table, column, row: JSON.stringify(row), includeSelf, refRowLimit }
  }).then(r => r.data)

export const getDeReferences = (group, database, table, column, row, refRowLimit = 100) =>
  client.get('/dereferences', {
    params: { group, database, table, column, row: JSON.stringify(row), refRowLimit }
  }).then(r => r.data)

export const traceRow = (group, database, table, row, refRowLimit = 100) =>
  client.get('/trace', {
    params: { group, database, table, row: JSON.stringify(row), refRowLimit }
  }).then(r => r.data)

export const addRow = (group, database, table, data) =>
  client.post('/row/add', data, { params: { group, database, table } }).then(r => r.data)

export const editRow = (group, database, table, pk, pkValue, data) =>
  client.put('/row/edit', data, { params: { group, database, table, pk, pkValue } }).then(r => r.data)

export const deleteRow = (group, database, table, pk, pkValue) =>
  client.delete('/row', { params: { group, database, table, pk, pkValue } }).then(r => r.data)
