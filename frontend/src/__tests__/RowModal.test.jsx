import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi, describe, test, expect, beforeEach } from 'vitest';
import { axe } from '../test/setup.js';
import RowModal from '../components/RowModal';

const defaultProps = {
  mode: 'add',
  columns: ['name', 'email'],
  row: null,
  pk: 'id',
  onSave: vi.fn(),
  onDelete: vi.fn(),
  onClose: vi.fn(),
};

describe('RowModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test('renders all column inputs in add mode', () => {
    render(<RowModal {...defaultProps} />);
    // Two empty inputs (name, email)
    expect(screen.getAllByDisplayValue('')).toHaveLength(2);
    expect(screen.getAllByRole('textbox')).toHaveLength(2);
  });

  test('populates inputs with row values in edit mode', () => {
    render(
      <RowModal
        {...defaultProps}
        mode="edit"
        row={{ name: 'Alice', email: 'alice@example.com' }}
      />
    );
    expect(screen.getByDisplayValue('Alice')).toBeInTheDocument();
    expect(screen.getByDisplayValue('alice@example.com')).toBeInTheDocument();
  });

  test('normalizes ISO datetime values in edit mode', () => {
    render(
      <RowModal
        {...defaultProps}
        mode="edit"
        columns={['created_at']}
        row={{ created_at: '2026-04-06T17:48:50.000+00:00' }}
      />
    );
    expect(screen.getByDisplayValue('2026-04-06 17:48:50')).toBeInTheDocument();
  });

  test('calls onSave with form data on submit', async () => {
    defaultProps.onSave.mockResolvedValue({});
    render(<RowModal {...defaultProps} />);

    const [nameInput] = screen.getAllByRole('textbox');
    fireEvent.change(nameInput, { target: { value: 'Bob' } });
    fireEvent.click(screen.getByRole('button', { name: /save|add/i }));

    await waitFor(() =>
      expect(defaultProps.onSave).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'Bob' })
      )
    );
  });

  test('calls onClose after successful save', async () => {
    defaultProps.onSave.mockResolvedValue({});
    render(<RowModal {...defaultProps} />);

    fireEvent.click(screen.getByRole('button', { name: /save|add/i }));

    await waitFor(() => expect(defaultProps.onClose).toHaveBeenCalled());
  });

  test('shows error message when onSave rejects', async () => {
    defaultProps.onSave.mockRejectedValue({ message: 'Duplicate entry' });
    render(<RowModal {...defaultProps} />);

    fireEvent.click(screen.getByRole('button', { name: /save|add/i }));

    await waitFor(() =>
      expect(screen.getByText(/duplicate entry/i)).toBeInTheDocument()
    );
  });

  test('calls onDelete when delete button is clicked in delete-confirm mode', async () => {
    defaultProps.onDelete.mockResolvedValue({});
    render(
      <RowModal
        {...defaultProps}
        mode="delete"
        row={{ id: 1, name: 'Alice', email: 'a@b.com' }}
        pk="id"
      />
    );

    const deleteBtn = screen.getByRole('button', { name: /^delete$/i });
    fireEvent.click(deleteBtn);

    await waitFor(() => expect(defaultProps.onDelete).toHaveBeenCalled());
  });

  test('closes on backdrop click', () => {
    const { container } = render(<RowModal {...defaultProps} />);
    // Click the outermost div (backdrop)
    fireEvent.click(container.firstChild);
    expect(defaultProps.onClose).toHaveBeenCalled();
  });

  test('has no accessibility violations', async () => {
    const { container } = render(<RowModal {...defaultProps} />);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
