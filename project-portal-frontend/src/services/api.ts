// src/services/api.ts
import axios from 'axios';

// For Create React App, use process.env
const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080/api';

export const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor - add auth token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('access_token');
    const userStr = localStorage.getItem('user');
    const user = userStr ? JSON.parse(userStr) : null;

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    try {
      const userStr = localStorage.getItem('user');
      if (userStr) {
        const user = JSON.parse(userStr);
        const userId = Number(user?.id);
        
        if (!isNaN(userId) && userId > 0) {
          config.headers['X-User-Id'] = userId;
        } else {
          config.headers['X-User-Id'] = 1;  // Default test user
        }
      } else {
        config.headers['X-User-Id'] = 1;  // Default if no user
      }
    } catch (e) {
      config.headers['X-User-Id'] = 1;  // Default on error
    }

    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor - handle auth errors
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('access_token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);