import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi, describe, test, expect, beforeEach } from 'vitest';
import { axe } from '../test/setup.js';
import LoginPage from '../pages/LoginPage';

vi.mock('../api/client', () => ({
  login: vi.fn(),
  getAuthConfig: vi.fn(),
}));

import * as client from '../api/client';

describe('LoginPage', () => {
  beforeEach(() => {
    client.getAuthConfig.mockResolvedValue({ oauth2Enabled: false });
    client.login.mockReset();
  });

  test('renders username and password fields', () => {
    render(<LoginPage onLogin={vi.fn()} />);
    expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  test('calls onLogin after successful form submit', async () => {
    client.login.mockResolvedValue({});
    const onLogin = vi.fn();
    render(<LoginPage onLogin={onLogin} />);

    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'admin' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'secret' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => expect(onLogin).toHaveBeenCalledOnce());
  });

  test('shows error message when login fails', async () => {
    client.login.mockRejectedValue({
      response: { data: { error: 'Invalid credentials' } },
    });
    render(<LoginPage onLogin={vi.fn()} />);

    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'admin' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'wrong' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() =>
      expect(screen.getByText(/invalid credentials/i)).toBeInTheDocument()
    );
  });

  test('shows fallback error when response has no error field', async () => {
    client.login.mockRejectedValue(new Error('Network error'));
    render(<LoginPage onLogin={vi.fn()} />);

    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'admin' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'pw' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() =>
      expect(screen.getByText(/invalid credentials/i)).toBeInTheDocument()
    );
  });

  test('shows Google sign-in button when oauth2 is enabled', async () => {
    client.getAuthConfig.mockResolvedValue({ oauth2Enabled: true });
    render(<LoginPage onLogin={vi.fn()} />);

    await waitFor(() =>
      expect(screen.getByText(/sign in with google/i)).toBeInTheDocument()
    );
  });

  test('does not show Google button when oauth2 is disabled', async () => {
    render(<LoginPage onLogin={vi.fn()} />);
    await waitFor(() => screen.getByRole('button', { name: /sign in/i }));
    expect(screen.queryByText(/sign in with google/i)).not.toBeInTheDocument();
  });

  test('disables submit button while signing in', async () => {
    let resolve;
    client.login.mockReturnValue(new Promise(r => { resolve = r; }));
    render(<LoginPage onLogin={vi.fn()} />);

    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'admin' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'pw' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /signing in/i })).toBeDisabled()
    );
    resolve({});
  });

  test('has no accessibility violations', async () => {
    const { container } = render(<LoginPage onLogin={vi.fn()} />);
    await waitFor(() => screen.getByRole('button', { name: /sign in/i }));
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
