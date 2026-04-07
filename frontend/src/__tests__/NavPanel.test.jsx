import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi, describe, test, expect, beforeEach } from 'vitest';
import { axe } from '../test/setup.js';
import NavPanel from '../components/NavPanel';

vi.mock('../api/client', () => ({
  getGroups: vi.fn(),
  getDatabases: vi.fn(),
  getTables: vi.fn(),
}));

import * as client from '../api/client';

const onTableSelect = vi.fn();

describe('NavPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    client.getGroups.mockResolvedValue(['prod', 'staging']);
    client.getDatabases.mockResolvedValue(['db1', 'db2']);
    client.getTables.mockResolvedValue([
      { name: 'users', primaryKey: 'id', remark: '' },
      { name: 'orders', primaryKey: 'order_id', remark: '' },
    ]);
  });

  test('renders brand name', () => {
    render(<NavPanel onTableSelect={onTableSelect} />);
    expect(screen.getByText(/fkblitz/i)).toBeInTheDocument();
  });

  test('renders group select on load', async () => {
    render(<NavPanel onTableSelect={onTableSelect} />);
    await waitFor(() => expect(client.getGroups).toHaveBeenCalled());
    // Both selects start with "— select —"; check options loaded into the group select
    expect(screen.getByText('prod')).toBeInTheDocument();
    expect(screen.getByText('staging')).toBeInTheDocument();
  });

  test('fetches databases when group is selected', async () => {
    render(<NavPanel onTableSelect={onTableSelect} />);
    await waitFor(() => expect(client.getGroups).toHaveBeenCalled());

    const groupSelect = screen.getAllByRole('combobox')[0];
    fireEvent.change(groupSelect, { target: { value: 'prod' } });

    await waitFor(() => expect(client.getDatabases).toHaveBeenCalledWith('prod'));
    expect(screen.getByText('db1')).toBeInTheDocument();
  });

  test('fetches tables when database is selected', async () => {
    render(<NavPanel onTableSelect={onTableSelect} />);
    await waitFor(() => expect(client.getGroups).toHaveBeenCalled());

    fireEvent.change(screen.getAllByRole('combobox')[0], { target: { value: 'prod' } });
    await waitFor(() => expect(client.getDatabases).toHaveBeenCalled());

    fireEvent.change(screen.getAllByRole('combobox')[1], { target: { value: 'db1' } });
    await waitFor(() => expect(client.getTables).toHaveBeenCalledWith('prod', 'db1'));

    expect(screen.getByText('users')).toBeInTheDocument();
    expect(screen.getByText('orders')).toBeInTheDocument();
  });

  test('calls onTableSelect with group, database, table, and primaryKey', async () => {
    render(<NavPanel onTableSelect={onTableSelect} />);
    await waitFor(() => expect(client.getGroups).toHaveBeenCalled());

    fireEvent.change(screen.getAllByRole('combobox')[0], { target: { value: 'prod' } });
    await waitFor(() => expect(client.getDatabases).toHaveBeenCalled());

    fireEvent.change(screen.getAllByRole('combobox')[1], { target: { value: 'db1' } });
    await waitFor(() => expect(client.getTables).toHaveBeenCalled());

    fireEvent.click(screen.getByText('users'));

    expect(onTableSelect).toHaveBeenCalledWith({
      group: 'prod',
      database: 'db1',
      table: 'users',
      primaryKey: 'id',
    });
  });

  test('database select is disabled until a group is chosen', async () => {
    render(<NavPanel onTableSelect={onTableSelect} />);
    await waitFor(() => expect(client.getGroups).toHaveBeenCalled());

    const dbSelect = screen.getAllByRole('combobox')[1];
    expect(dbSelect).toBeDisabled();
  });

  test('has no accessibility violations', async () => {
    const { container } = render(<NavPanel onTableSelect={onTableSelect} />);
    await waitFor(() => expect(client.getGroups).toHaveBeenCalled());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
