// src/services/auth.service.ts
import { loginApi } from './api';
import { User, AuthResponse } from '../types';

const TOKEN_KEY = 'access_token';
const USER_KEY = 'user';

export const authService = {
    // Token management
    setToken(token: string) {
        localStorage.setItem(TOKEN_KEY, token);
    },

    getToken(): string | null {
        return localStorage.getItem(TOKEN_KEY);
    },

    clearToken() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
    },

    setUser(user: User) {
        localStorage.setItem(USER_KEY, JSON.stringify(user));
    },

    getUser(): User | null {
        const userStr = localStorage.getItem(USER_KEY);
        if (!userStr) return null;
        try {
            return JSON.parse(userStr);
        } catch {
            return null;
        }
    },

    isAuthenticated(): boolean {
        return !!this.getToken();
    },

    // API calls
    async login(username: string, password: string): Promise<AuthResponse> {
        const response = await loginApi.post<AuthResponse>('/auth/login', { username, password });
        if (response.data.token) {
            this.setToken(response.data.token);
            this.setUser(response.data.user);
        }
        return response.data;
    },

    async register(username: string, email: string, password: string): Promise<AuthResponse> {
        const response = await loginApi.post<AuthResponse>('/auth/register', { username, email, password });
        if (response.data.token) {
            this.setToken(response.data.token);
            this.setUser(response.data.user);
        }
        return response.data;
    },

    async getCurrentUser(): Promise<User> {
        const response = await loginApi.get<User>('/auth/me');
        return response.data;
    },

    logout() {
        this.clearToken();
        window.location.replace('/login');
    },
};