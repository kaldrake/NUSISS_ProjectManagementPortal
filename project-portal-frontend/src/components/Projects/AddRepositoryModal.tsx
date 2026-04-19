// src/components/Projects/AddRepositoryModal.tsx
import React, { useState, useEffect, useCallback } from 'react';
import Select from 'react-select';
import toast from 'react-hot-toast';
import { projectService } from '../../services/project.service';
import { githubService } from '../../services/github.service';
import { GitHubRepository, Repository } from '../../types';

interface AddRepositoryModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  projectId: string;
  existingRepos: Repository[];
}

const AddRepositoryModal: React.FC<AddRepositoryModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
  projectId,
  existingRepos,
}) => {
  const [repositories, setRepositories] = useState<GitHubRepository[]>([]);
  const [selectedRepo, setSelectedRepo] = useState<GitHubRepository | null>(null);
  const [loading, setLoading] = useState(true);
  const [adding, setAdding] = useState(false);

  // Wrap fetchRepositories in useCallback to fix dependency warning
  const fetchRepositories = useCallback(async () => {
    try {
      setLoading(true);
      const repos = await githubService.getRepositories();
      // Filter out already added repos
      const existingFullNames = new Set(existingRepos.map((r: Repository) => r.repoFullName));
      const availableRepos = repos.filter((r: GitHubRepository) => !existingFullNames.has(r.full_name));
      setRepositories(availableRepos);
    } catch (err) {
      toast.error('Failed to load GitHub repositories');
      onClose();
    } finally {
      setLoading(false);
    }
  }, [existingRepos, onClose]); // Add dependencies

  useEffect(() => {
    if (isOpen) {
      fetchRepositories();
    }
  }, [isOpen, fetchRepositories]); // Add fetchRepositories to dependency array

  const handleAdd = async () => {
    if (!selectedRepo) {
      toast.error('Please select a repository');
      return;
    }

    setAdding(true);
    try {
      await projectService.addRepository(projectId, {
        repoFullName: selectedRepo.full_name,
        repoUrl: selectedRepo.html_url,
        defaultBranch: selectedRepo.default_branch,
      });
      onSuccess();
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Failed to add repository');
    } finally {
      setAdding(false);
    }
  };

  if (!isOpen) return null;

  const options = repositories.map((repo: GitHubRepository) => ({
    value: repo,
    label: `${repo.full_name} • ${repo.language || 'Unknown'} • ${repo.private ? 'Private' : 'Public'}`,
  }));

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto">
      <div className="flex items-center justify-center min-h-screen px-4 pt-4 pb-20 text-center sm:block sm:p-0">
        <div className="fixed inset-0 transition-opacity bg-gray-500 bg-opacity-75" onClick={onClose} />

        <div className="inline-block align-bottom bg-white rounded-lg text-left overflow-hidden shadow-xl transform transition-all sm:my-8 sm:align-middle sm:max-w-lg sm:w-full">
          <div className="bg-white px-4 pt-5 pb-4 sm:p-6 sm:pb-4">
            <div>
              <h3 className="text-lg font-medium leading-6 text-gray-900 mb-4">
                Add GitHub Repository
              </h3>

              {loading ? (
                <div className="flex justify-center py-8">
                  <div className="w-8 h-8 border-2 border-blue-600 border-t-transparent rounded-full animate-spin" />
                </div>
              ) : repositories.length === 0 ? (
                <div className="text-center py-8">
                  <p className="text-gray-500">No available repositories</p>
                  <p className="text-sm text-gray-400 mt-1">
                    All your GitHub repositories have been added already
                  </p>
                </div>
              ) : (
                <>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Select Repository
                  </label>
                  <Select
                    options={options}
                    onChange={(option) => setSelectedRepo(option?.value || null)}
                    placeholder="Search your repositories..."
                    className="react-select-container"
                    classNamePrefix="react-select"
                  />

                  {selectedRepo && (
                    <div className="mt-4 p-3 bg-gray-50 rounded-lg">
                      <p className="text-sm text-gray-600">
                        <span className="font-medium">Default branch:</span> {selectedRepo.default_branch}
                      </p>
                      <p className="text-sm text-gray-600">
                        <span className="font-medium">Visibility:</span> {selectedRepo.private ? 'Private' : 'Public'}
                      </p>
                      {selectedRepo.description && (
                        <p className="text-sm text-gray-500 mt-1">{selectedRepo.description}</p>
                      )}
                    </div>
                  )}
                </>
              )}
            </div>
          </div>

          <div className="bg-gray-50 px-4 py-3 sm:px-6 sm:flex sm:flex-row-reverse">
            <button
              onClick={handleAdd}
              disabled={!selectedRepo || adding || loading || repositories.length === 0}
              className="w-full inline-flex justify-center rounded-md border border-transparent shadow-sm px-4 py-2 bg-blue-600 text-base font-medium text-white hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 sm:ml-3 sm:w-auto sm:text-sm disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {adding ? 'Adding...' : 'Add Repository'}
            </button>
            <button
              onClick={onClose}
              className="mt-3 w-full inline-flex justify-center rounded-md border border-gray-300 shadow-sm px-4 py-2 bg-white text-base font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 sm:mt-0 sm:ml-3 sm:w-auto sm:text-sm"
            >
              Cancel
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AddRepositoryModal;