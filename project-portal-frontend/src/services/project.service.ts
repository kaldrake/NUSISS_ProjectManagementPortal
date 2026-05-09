// src/services/project.service.ts
import { projectApi } from './api';
import { Project, Repository, ProjectCreateDTO, ProjectUpdateDTO } from '../types';

export const projectService = {
    // =============================================
    // Project CRUD Operations
    // =============================================
    
    async getProjects(): Promise<Project[]> {
        const response = await projectApi.get<Project[]>('/projects');
        return response.data;
    },

    async getProject(id: string): Promise<Project> {
        const response = await projectApi.get<Project>(`/projects/${id}`);
        return response.data;
    },

    async createProject(data: ProjectCreateDTO): Promise<Project> {
        const response = await projectApi.post<Project>('/projects', data);
        return response.data;
    },

    async updateProject(id: string, data: ProjectUpdateDTO): Promise<Project> {
        const response = await projectApi.put<Project>(`/projects/${id}`, data);
        return response.data;
    },

    async deleteProject(id: string): Promise<void> {
        await projectApi.delete(`/projects/${id}`);
    },

    // =============================================
    // Repository Operations
    // =============================================
    
    async getRepositories(projectId: string): Promise<Repository[]> {
        const response = await projectApi.get<Repository[]>(`/projects/${projectId}/repositories`);
        return response.data;
    },

    async addRepository(projectId: string, data: { 
        repoFullName: string; 
        repoUrl: string; 
        defaultBranch?: string 
    }): Promise<Repository> {
        const response = await projectApi.post<Repository>(`/projects/${projectId}/repositories`, data);
        return response.data;
    },

    async removeRepository(projectId: string, repoId: string): Promise<void> {
        await projectApi.delete(`/projects/${projectId}/repositories/${repoId}`);
    },

    // =============================================
    // Scan Trigger (calls Project Service which calls Scan Service)
    // =============================================
    
    async triggerScan(projectId: string, repoId: string): Promise<void> {
        await projectApi.post(`/projects/${projectId}/repositories/${repoId}/scan`);
    },
};