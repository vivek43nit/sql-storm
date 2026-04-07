import { render, screen, fireEvent } from '@testing-library/react';
import { vi, describe, test, expect, beforeEach } from 'vitest';
import { axe } from '../test/setup.js';
import TableGrid from '../components/TableGrid';

vi.mock('../api/client', () => ({
  getReferences: vi.fn(),
  getDeReferences: vi.fn(),
  traceRow: vi.fn(),
  addRow: vi.fn(),
  editRow: vi.fn(),
  deleteRow: vi.fn(),
}));

const baseResultSet = {
  group: 'prod',
  database: 'mydb',
  table: 'users',
  columns: ['id', 'name', 'email'],
  rows: [
    { id: 1, name: 'Alice', email: 'alice@example.com' },
    { id: 2, name: 'Bob', email: 'bob@example.com' },
  ],
  pk: 'id',
  updatable: true,
  deletable: true,
  relation: 'self',
  info: 'SELF',
  referToColumns: {},
  referencedByColumns: {},
};

describe('TableGrid', () => {
  beforeEach(() => vi.clearAllMocks());

  test('returns null when resultSet is not provided', () => {
    const { container } = render(
      <TableGrid resultSet={null} group="prod" userRole="READ_WRITE" />
    );
    expect(container.firstChild).toBeNull();
  });

  test('renders table name and row count', () => {
    render(<TableGrid resultSet={baseResultSet} group="prod" userRole="READ_ONLY" />);
    expect(screen.getByText('users')).toBeInTheDocument();
    expect(screen.getByText(/2 rows/)).toBeInTheDocument();
  });

  test('renders all column headers', () => {
    render(<TableGrid resultSet={baseResultSet} group="prod" userRole="READ_ONLY" />);
    expect(screen.getByText('id')).toBeInTheDocument();
    expect(screen.getByText('name')).toBeInTheDocument();
    expect(screen.getByText('email')).toBeInTheDocument();
  });

  test('renders row data', () => {
    render(<TableGrid resultSet={baseResultSet} group="prod" userRole="READ_ONLY" />);
    expect(screen.getByText('Alice')).toBeInTheDocument();
    expect(screen.getByText('bob@example.com')).toBeInTheDocument();
  });

  test('shows Add Row, Edit, Del buttons for READ_WRITE with updatable+deletable result', () => {
    render(<TableGrid resultSet={baseResultSet} group="prod" userRole="READ_WRITE" />);
    expect(screen.getByRole('button', { name: /add row/i })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /edit/i })).toHaveLength(2);
    expect(screen.getAllByRole('button', { name: /del/i })).toHaveLength(2);
  });

  test('hides Add Row, Edit, Del buttons for READ_ONLY', () => {
    render(<TableGrid resultSet={baseResultSet} group="prod" userRole="READ_ONLY" />);
    expect(screen.queryByRole('button', { name: /add row/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /edit/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /del/i })).not.toBeInTheDocument();
  });

  test('shows Add Row button for ADMIN', () => {
    render(<TableGrid resultSet={baseResultSet} group="prod" userRole="ADMIN" />);
    expect(screen.getByRole('button', { name: /add row/i })).toBeInTheDocument();
  });

  test('renders ↗ FK chip for referTo columns', () => {
    const rs = {
      ...baseResultSet,
      referToColumns: { email: ['auth.accounts.email'] },
    };
    render(<TableGrid resultSet={rs} group="prod" userRole="READ_ONLY" />);
    // ↗ appears in both the column header and the FK legend row
    expect(screen.getAllByText('↗').length).toBeGreaterThan(0);
  });

  test('renders ↙ FK chip for referencedBy columns', () => {
    const rs = {
      ...baseResultSet,
      referencedByColumns: { id: ['orders.users.user_id'] },
    };
    render(<TableGrid resultSet={rs} group="prod" userRole="READ_ONLY" />);
    // ↙ appears in both the column header and the FK legend row
    expect(screen.getAllByText('↙').length).toBeGreaterThan(0);
  });

  test('renders referTo chip label for FK result set', () => {
    const rs = {
      ...baseResultSet,
      relation: 'referTo',
      info: 'orders.user_id -> users.id',
    };
    render(<TableGrid resultSet={rs} group="prod" userRole="READ_ONLY" />);
    // Chip shows a → arrow
    expect(screen.getByText(/→/)).toBeInTheDocument();
  });

  test('Edit button opens modal', () => {
    render(<TableGrid resultSet={baseResultSet} group="prod" userRole="READ_WRITE" />);
    fireEvent.click(screen.getAllByRole('button', { name: /edit/i })[0]);
    expect(screen.getByText('Edit Row')).toBeInTheDocument();
  });

  test('Del button opens delete confirmation modal', () => {
    render(<TableGrid resultSet={baseResultSet} group="prod" userRole="READ_WRITE" />);
    fireEvent.click(screen.getAllByRole('button', { name: /del/i })[0]);
    expect(screen.getByText('Delete Row')).toBeInTheDocument();
  });

  test('has no accessibility violations', async () => {
    const { container } = render(
      <TableGrid resultSet={baseResultSet} group="prod" userRole="READ_ONLY" />
    );
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
