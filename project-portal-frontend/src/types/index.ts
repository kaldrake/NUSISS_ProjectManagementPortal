// src/types/index.ts
export interface User {
  id: string;
  username: string;
  email: string;
  role: 'ADMIN' | 'DEVELOPER' | 'VIEWER';
  githubId: number;
  avatarUrl?: string;
}

export interface Project {
  id: string;
  name: string;
  description: string;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
  repositories?: Repository[];
  vulnerabilityCount?: number;
  criticalCount?: number;
}

export interface Repository {
  id: string;
  projectId: string;
  githubRepoId: number;
  repoName: string;
  repoFullName: string;
  repoUrl: string;
  cloneUrl: string;
  defaultBranch: string;
  isActive: boolean;
  lastScanAt: string | null;
  createdAt: string;
  vulnerabilityCount?: number;
  criticalCount?: number;
  majorCount?: number;
}

export interface GitHubRepository {
  id: number;
  name: string;
  full_name: string;
  html_url: string;
  clone_url: string;
  default_branch: string;
  private: boolean;
  description: string | null;
  language: string | null;
}

export interface Vulnerability {
  id: string;
  scanId: string;
  sonarqubeKey: string;
  vulnerabilityType: string;
  severity: 'BLOCKER' | 'CRITICAL' | 'MAJOR' | 'MINOR' | 'INFO';
  filePath: string;
  lineNumber: number;
  message: string;
  status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'FALSE_POSITIVE';
  aiSuggestion?: string;
  createdAt: string;
}

export interface Scan {
  id: string;
  repositoryId: string;
  scanStatus: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  issuesCount: number;
  criticalCount: number;
  majorCount: number;
  startedAt: string | null;
  completedAt: string | null;
}

export interface AuthResponse {
  token: string;
  user: User;
}