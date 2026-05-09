// frontend/src/services/api.ts
import axios from 'axios';

// Service URLs
const LOGIN_SERVICE_URL = process.env.REACT_APP_LOGIN_API_URL || 'http://localhost:8081/api';
const PROJECT_SERVICE_URL = process.env.REACT_APP_PROJECT_API_URL || 'http://localhost:8082/api';
const SCAN_SERVICE_URL = process.env.REACT_APP_SCAN_API_URL || 'http://localhost:8083/api';

// Create axios instances for each service
export const loginApi = axios.create({
    baseURL: LOGIN_SERVICE_URL,
    headers: { 'Content-Type': 'application/json' },
});

export const projectApi = axios.create({
    baseURL: PROJECT_SERVICE_URL,
    headers: { 'Content-Type': 'application/json' },
});

export const scanApi = axios.create({
    baseURL: SCAN_SERVICE_URL,
    headers: { 'Content-Type': 'application/json' },
});

// Auth interceptor for all services
const authInterceptor = (config: any) => {
    const token = localStorage.getItem('access_token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    
    const userStr = localStorage.getItem('user');
    if (userStr) {
        try {
            const user = JSON.parse(userStr);
            if (user?.id) {
                config.headers['X-User-Id'] = Number(user.id);
            }
        } catch (e) {
            console.error('Failed to parse user', e);
        }
    }
    
    return config;
};

// Apply interceptor to all services
loginApi.interceptors.request.use(authInterceptor);
projectApi.interceptors.request.use(authInterceptor);
scanApi.interceptors.request.use(authInterceptor);

// Response interceptor for handling auth errors
const responseInterceptor = (error: any) => {
    if (error.response?.status === 401) {
        localStorage.removeItem('access_token');
        localStorage.removeItem('user');
        window.location.href = '/login';
    }
    return Promise.reject(error);
};

loginApi.interceptors.response.use((response) => response, responseInterceptor);
projectApi.interceptors.response.use((response) => response, responseInterceptor);
scanApi.interceptors.response.use((response) => response, responseInterceptor);