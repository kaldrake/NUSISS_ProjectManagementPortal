// src/services/project.service.ts
import { api } from './api';
import { Project, Repository, Vulnerability, Scan } from '../types';

export const projectService = {
  // Projects
  async getProjects(): Promise<Project[]> {
    const response = await api.get<Project[]>('/projects');
    return response.data;
  },

  async getProject(id: string): Promise<Project> {
    const response = await api.get<Project>(`/projects/${id}`);
    return response.data;
  },

  async createProject(data: { name: string; description?: string; repositoryUrl?: string; repositoryName?: string }): Promise<Project> {
    
    // Get user ID from localStorage
    const userStr = localStorage.getItem('user');
    const user = userStr ? JSON.parse(userStr) : null;
    
    const response = await api.post<Project>('/projects', data, {
      headers: {
        'X-User-Id': user ? user.id : 1
      }
    });
    return response.data;
  },

  async updateProject(id: string, data: Partial<Project>): Promise<Project> {
    const response = await api.put<Project>(`/projects/${id}`, data);
    return response.data;
  },

  async deleteProject(id: string): Promise<void> {
    await api.delete(`/projects/${id}`);
  },

  // Repositories
  async getRepositories(projectId: string): Promise<Repository[]> {
    const response = await api.get<Repository[]>(`/projects/${projectId}/repositories`);
    return response.data;
  },

  async addRepository(projectId: string, data: { 
    repoFullName: string; 
    repoUrl: string; 
    defaultBranch: string 
  }): Promise<Repository> {
    const response = await api.post<Repository>(`/projects/${projectId}/repositories`, data);
    return response.data;
  },

  async removeRepository(projectId: string, repoId: string): Promise<void> {
    await api.delete(`/projects/${projectId}/repositories/${repoId}`);
  },

  async triggerScan(repoId: string): Promise<void> {
    await api.post(`/repositories/${repoId}/scan`);
  },

  // Vulnerabilities
  async getVulnerabilities(repoId: string): Promise<Vulnerability[]> {
    const response = await api.get<Vulnerability[]>(`/repositories/${repoId}/vulnerabilities`);
    return response.data;
  },

  async updateVulnerabilityStatus(vulnId: string, status: string): Promise<void> {
    await api.patch(`/vulnerabilities/${vulnId}`, { status });
  },

  // Scans
  async getScans(repoId: string): Promise<Scan[]> {
    const response = await api.get<Scan[]>(`/repositories/${repoId}/scans`);
    return response.data;
  },
  
};