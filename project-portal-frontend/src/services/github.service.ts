// src/services/github.service.ts
import { scanApi } from './api';

// Define the GitHub repository type locally
interface GitHubRepo {
  id: number;
  full_name: string;
  html_url: string;
  default_branch: string;
  language: string | null;
  private: boolean;
  description: string | null;
}

export const githubService = {
  // Get user's GitHub repositories (from Project Service)
  async getRepositories(): Promise<GitHubRepo[]> {
    const response = await scanApi.get<GitHubRepo[]>('/github/repositories');
    return response.data;
  },

  // Validate a GitHub repository URL
  async validateRepoUrl(url: string): Promise<{ isValid: boolean; repoName?: string }> {
    const response = await scanApi.post('/github/validate', { url });
    return response.data;
  },
};