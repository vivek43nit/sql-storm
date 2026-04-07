import { render, screen, waitFor } from '@testing-library/react';
import { vi, describe, test, expect, beforeEach } from 'vitest';
import App from '../App';

vi.mock('../api/client', () => ({
  getMe: vi.fn(),
  getGroups: vi.fn(),
  getAuthConfig: vi.fn(),
  logout: vi.fn(),
}));

import * as client from '../api/client';

describe('App', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    client.getAuthConfig.mockResolvedValue({ oauth2Enabled: false });
  });

  test('shows loading state while checking auth', () => {
    // getMe never resolves during this test
    client.getMe.mockReturnValue(new Promise(() => {}));
    render(<App />);
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  test('shows LoginPage when not authenticated', async () => {
    client.getMe.mockRejectedValue(new Error('401'));
    render(<App />);

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument()
    );
  });

  test('shows main content when authenticated', async () => {
    client.getMe.mockResolvedValue({
      username: 'alice',
      role: 'READ_ONLY',
      permissions: [],
    });
    // getGroups is called by NavPanel — return empty to avoid further fetching
    client.getGroups = vi.fn().mockResolvedValue([]);

    render(<App />);

    // MainPage is rendered — contains the QueryBar or navigation area
    await waitFor(() =>
      // The main page renders a top bar; login button is gone
      expect(screen.queryByRole('button', { name: /sign in/i })).not.toBeInTheDocument()
    );
  });

  test('returns to login when fkblitz:unauthorized event fires', async () => {
    client.getMe
      .mockResolvedValueOnce({ username: 'alice', role: 'READ_ONLY', permissions: [] })
      .mockRejectedValue(new Error('401'));
    client.getGroups = vi.fn().mockResolvedValue([]);

    render(<App />);

    await waitFor(() =>
      expect(screen.queryByRole('button', { name: /sign in/i })).not.toBeInTheDocument()
    );

    // Simulate a 401 interceptor event
    window.dispatchEvent(new CustomEvent('fkblitz:unauthorized'));

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument()
    );
  });
});
