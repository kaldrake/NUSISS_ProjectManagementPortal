// src/services/scan.service.ts
import { scanApi } from './api';
import { Vulnerability, Scan, ScanStatus, DashboardSummary } from '../types';

export const scanService = {
    // =============================================
    // Vulnerability Operations
    // =============================================
    
    async getVulnerabilitiesByRepo(repoId: string): Promise<Vulnerability[]> {
        const response = await scanApi.get<Vulnerability[]>(`/scans/repositories/${repoId}/vulnerabilities`);
        return response.data;
    },

    async getVulnerabilitiesByProject(projectId: string): Promise<Vulnerability[]> {
        const response = await scanApi.get<Vulnerability[]>(`/scans/projects/${projectId}/vulnerabilities`);
        return response.data;
    },

    async getVulnerabilityById(vulnId: string): Promise<Vulnerability> {
        const response = await scanApi.get<Vulnerability>(`/scans/vulnerabilities/${vulnId}`);
        return response.data;
    },

    async updateVulnerabilityStatus(vulnId: string, status: string): Promise<void> {
        await scanApi.patch(`/scans/vulnerabilities/${vulnId}/status`, null, {
            params: { status }
        });
    },

    // =============================================
    // Scan Operations
    // =============================================
    
    async getScans(repoId: string): Promise<Scan[]> {
        const response = await scanApi.get<Scan[]>(`/scans/repositories/${repoId}/history`);
        return response.data;
    },

    async getScanStatus(scanId: string): Promise<ScanStatus> {
        const response = await scanApi.get<ScanStatus>(`/scans/${scanId}/status`);
        return response.data;
    },

    async getScanVulnerabilities(scanId: string): Promise<Vulnerability[]> {
        const response = await scanApi.get<Vulnerability[]>(`/scans/${scanId}/vulnerabilities`);
        return response.data;
    },

    // =============================================
    // Dashboard Operations
    // =============================================
    
    async getDashboardSummary(projectId: string): Promise<DashboardSummary> {
        const response = await scanApi.get<DashboardSummary>(`/scans/dashboard/projects/${projectId}/summary`);
        return response.data;
    },
};