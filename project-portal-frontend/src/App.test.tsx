import React from 'react';
import { render, screen } from '@testing-library/react';
import App from './App';

test('renders login page heading', () => {
  render(<App />);
  const heading = screen.getByText(/project portal/i);
  expect(heading).toBeInTheDocument();
});
