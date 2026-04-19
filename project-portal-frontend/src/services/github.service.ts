// src/services/github.service.ts
import { api } from './api';
import { GitHubRepository } from '../types';

export const githubService = {
  // Get all repositories from GitHub
  async getRepositories(): Promise<GitHubRepository[]> {
    const response = await api.get<GitHubRepository[]>('/github/repositories');
    return response.data;
  },

  // Link a GitHub repository to the current project
  async linkRepository(projectId: string, repoData: {
    repoUrl: string;
    repoName: string;
    branch?: string;
  }) {
    const response = await api.post(`/projects/${projectId}/repositories`, repoData);
    return response.data;
  },

  // Get linked repositories for a project
  async getLinkedRepositories(projectId: string) {
    const response = await api.get(`/projects/${projectId}/repositories`);
    return response.data;
  },

  // Trigger a scan on a repository
  async triggerScan(repoId: string) {
    const response = await api.post(`/repositories/${repoId}/scan`);
    return response.data;
  },

  // Get scan results
  async getScanResults(repoId: string) {
    const response = await api.get(`/repositories/${repoId}/scans`);
    return response.data;
  },

  // For demo purposes - validate repository URL
  async validateRepoUrl(url: string): Promise<{ isValid: boolean; repoName?: string }> {
    const response = await api.post('/github/validate', { url });
    return response.data;
  },
};