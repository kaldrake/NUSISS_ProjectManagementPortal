// src/types/index.ts

// =============================================
// Auth Types
// =============================================

export interface User {
    id: number;
    username: string;
    email: string;
    role?: string;
    createdAt?: string;
}

export interface AuthResponse {
    token: string;
    user: User;
}

// =============================================
// GitHub Types
// =============================================

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

// =============================================
// Project Types
// =============================================

export interface Project {
    id: string;
    name: string;
    description?: string;
    ownerId: number;
    createdAt: string;
    updatedAt?: string;
    repositoryCount?: number;
    repositories?: Repository[];
    vulnerabilityCount?: number;
    criticalCount?: number;
    vulnerabilities?: Vulnerability[];
}

export interface ProjectCreateDTO {
    name: string;
    description?: string;
}

export interface ProjectUpdateDTO {
    name?: string;
    description?: string;
}

// =============================================
// Repository Types
// =============================================

export interface Repository {
    id: string;
    projectId: string;
    githubRepoId: number;
    repoName: string;
    repoFullName: string;
    repoUrl: string;
    cloneUrl?: string;
    defaultBranch: string;
    isActive: boolean;
    lastScanAt?: string;
    createdAt: string;
    vulnerabilityCount?: number;
    criticalCount?: number;
    majorCount?: number;
    vulnerabilities?: Vulnerability[];
}

// =============================================
// Vulnerability Types
// =============================================

export interface Vulnerability {
    id: string;
    scanId: string;
    sonarqubeRuleId?: string;
    vulnerabilityType?: string;
    severity: 'BLOCKER' | 'CRITICAL' | 'MAJOR' | 'MINOR' | 'INFO';
    filePath: string;
    lineNumber: number;
    message: string;
    status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'FALSE_POSITIVE';
    createdAt: string;
    aiSuggestion?: AiSuggestion | string;  // ← Allow both types
}

export interface AiSuggestion {
    id: string;
    suggestionText: string;
    codeExample?: string;
    confidenceScore: number;
    modelUsed: string;
    generatedAt: string;
}

// =============================================
// Scan Types
// =============================================

export interface Scan {
    id: string;
    projectId: string;
    repositoryId: string;
    repositoryUrl: string;
    branch: string;
    scanStatus: 'PENDING' | 'SCANNING' | 'COMPLETED' | 'FAILED';
    startedAt: string;
    completedAt?: string;
    errorMessage?: string;
    createdAt: string;
}

export interface ScanStatus {
    scanId: string;
    status: string;
    startedAt: string;
    completedAt?: string;
    errorMessage?: string;
    totalVulnerabilities: number;
    criticalCount: number;
}

// =============================================
// Dashboard Types
// =============================================

export interface DashboardSummary {
    totalVulnerabilities: number;
    blockerCount: number;
    criticalCount: number;
    majorCount: number;
    minorCount: number;
    infoCount: number;
    lastScanAt?: string;
    lastScanStatus?: string;
}