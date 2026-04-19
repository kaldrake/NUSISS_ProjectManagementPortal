// src/components/Projects/RepositoryList.tsx
import React, { useState } from 'react';
import { Repository } from '../../types';
import { projectService } from '../../services/project.service';
// Remove this line - SeverityBadge is not used
// import SeverityBadge from '../Common/SeverityBadge';
import toast from 'react-hot-toast';

interface RepositoryListProps {
  repositories: Repository[];
  onAddRepo: () => void;
  onScanTriggered: () => void;
  onRefresh: () => void;
}

const RepositoryList: React.FC<RepositoryListProps> = ({
  repositories,
  onAddRepo,
  onScanTriggered,
  onRefresh,
}) => {
  const [scanningRepoId, setScanningRepoId] = useState<string | null>(null);

  const handleTriggerScan = async (repoId: string) => {
    setScanningRepoId(repoId);
    try {
      await projectService.triggerScan(repoId);
      toast.success('Scan triggered successfully');
      onScanTriggered();
    } catch (err) {
      toast.error('Failed to trigger scan');
    } finally {
      setScanningRepoId(null);
    }
  };

  const handleRemoveRepo = async (repoId: string, repoName: string) => {
    const userConfirmed = window.confirm(`Remove "${repoName}" from this project?`);
    
    if (!userConfirmed) return;
    
    try {
      await projectService.removeRepository(repositories[0].projectId, repoId);
      toast.success('Repository removed');
      onRefresh();
    } catch (err) {
      toast.error('Failed to remove repository');
    }
  };

  if (repositories.length === 0) {
    return (
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-12 text-center">
        <svg className="mx-auto h-12 w-12 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
        </svg>
        <h3 className="mt-2 text-sm font-medium text-gray-900">No repositories</h3>
        <p className="mt-1 text-sm text-gray-500">Add a GitHub repository to start scanning for vulnerabilities.</p>
        <div className="mt-6">
          <button
            onClick={onAddRepo}
            className="inline-flex items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700"
          >
            Add Repository
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <h2 className="text-lg font-semibold text-gray-800">
          Repositories ({repositories.length})
        </h2>
        <button
          onClick={onAddRepo}
          className="bg-blue-600 hover:bg-blue-700 text-white px-3 py-1.5 rounded-lg text-sm font-medium flex items-center gap-1"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          Add Repository
        </button>
      </div>

      <div className="space-y-3">
        {repositories.map((repo) => (
          <div key={repo.id} className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
            <div className="flex justify-between items-start">
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-1">
                  <a
                    href={repo.repoUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-blue-600 hover:text-blue-700 font-medium"
                  >
                    {repo.repoFullName}
                  </a>
                  {!repo.isActive && (
                    <span className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded">Inactive</span>
                  )}
                </div>
                <div className="flex items-center gap-4 text-sm text-gray-500">
                  <span>Branch: {repo.defaultBranch}</span>
                  {repo.lastScanAt && (
                    <span>Last scan: {new Date(repo.lastScanAt).toLocaleString()}</span>
                  )}
                </div>
                {(repo.criticalCount !== undefined || repo.majorCount !== undefined) && (
                  <div className="flex items-center gap-3 mt-2">
                    {repo.criticalCount !== undefined && repo.criticalCount > 0 && (
                      <span className="text-xs bg-red-100 text-red-700 px-2 py-0.5 rounded">
                        {repo.criticalCount} critical
                      </span>
                    )}
                    {repo.majorCount !== undefined && repo.majorCount > 0 && (
                      <span className="text-xs bg-orange-100 text-orange-700 px-2 py-0.5 rounded">
                        {repo.majorCount} major
                      </span>
                    )}
                  </div>
                )}
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => handleTriggerScan(repo.id)}
                  disabled={scanningRepoId === repo.id}
                  className="text-gray-500 hover:text-gray-700 text-sm font-medium disabled:opacity-50"
                >
                  {scanningRepoId === repo.id ? (
                    <div className="w-5 h-5 border-2 border-gray-500 border-t-transparent rounded-full animate-spin" />
                  ) : (
                    'Scan Now'
                  )}
                </button>
                <button
                  onClick={() => handleRemoveRepo(repo.id, repo.repoFullName)}
                  className="text-red-500 hover:text-red-700 text-sm"
                >
                  Remove
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default RepositoryList;