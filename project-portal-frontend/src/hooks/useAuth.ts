// src/hooks/useAuth.ts
import { useState, useEffect } from 'react';
import { authService } from '../services/auth.service';
import { User } from '../types';

export const useAuth = () => {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [loading, setLoading] = useState(true);
  const [user, setUser] = useState<User | null>(null);

  useEffect(() => {
    const checkAuth = async () => {
      const token = authService.getToken();
      
      // Check for mock token (bypass login)
      if (token === 'mock-jwt-token-for-testing') {
        const mockUser = authService.getUser();
        if (mockUser) {
          setIsAuthenticated(true);
          setUser(mockUser);
          setLoading(false);
          return;
        }
      }
      
      // Normal authentication
      if (token) {
        // For now, just check if token exists
        // In production, you'd validate with backend
        setIsAuthenticated(true);
        const storedUser = authService.getUser();
        setUser(storedUser);
      }
      
      setLoading(false);
    };

    checkAuth();
  }, []);

  const logout = () => {
    authService.logout();
    setIsAuthenticated(false);
    setUser(null);
  };

  return { isAuthenticated, loading, user, logout };
};