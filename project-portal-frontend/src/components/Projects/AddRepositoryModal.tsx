// src/components/Projects/AddRepositoryModal.tsx
import React, { useState } from 'react';
import toast from 'react-hot-toast';
import { projectService } from '../../services/project.service';

interface AddRepositoryModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  projectId: string;
  existingRepos: any[];
}

interface RepoInfo {
  id: number;
  name: string;
  fullName: string;
  cloneUrl: string;
  defaultBranch: string;
}

const AddRepositoryModal: React.FC<AddRepositoryModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
  projectId,
  existingRepos,
}) => {
  const [repoUrl, setRepoUrl] = useState('');
  const [repoBranch, setRepoBranch] = useState('master');
  const [loading, setLoading] = useState(false);
  const [validating, setValidating] = useState(false);
  const [repoValid, setRepoValid] = useState(false);
  const [repoInfo, setRepoInfo] = useState<RepoInfo | null>(null);

  const handleValidateRepo = async () => {
    if (!repoUrl.trim()) {
      toast.error('Please enter a repository URL');
      return;
    }

    setValidating(true);
    try {
      let url = repoUrl.trim();
      if (url.endsWith('.git')) {
        url = url.slice(0, -4);
      }
      
      const match = url.match(/github\.com\/([^\/]+)\/([^\/]+)/);
      if (!match) {
        toast.error('Invalid GitHub URL format');
        setRepoValid(false);
        return;
      }
      
      const owner = match[1];
      const repo = match[2];
      
      const response = await fetch(`https://api.github.com/repos/${owner}/${repo}`);
      
      if (response.ok) {
        const data = await response.json();
        setRepoInfo({
          id: data.id,
          name: data.name,
          fullName: data.full_name,
          cloneUrl: data.clone_url,
          defaultBranch: data.default_branch
        });
        setRepoValid(true);
        toast.success(`Repository "${data.full_name}" is valid!`);
      } else {
        setRepoValid(false);
        toast.error('Repository not found or not accessible');
      }
    } catch (error) {
      console.error('Validation error:', error);
      setRepoValid(false);
      toast.error('Failed to validate repository');
    } finally {
      setValidating(false);
    }
  };

  const handleAdd = async () => {
    if (!repoUrl.trim()) {
      toast.error('Please enter a repository URL');
      return;
    }

    if (!repoValid || !repoInfo) {
      toast.error('Please validate the repository URL first');
      return;
    }

    setLoading(true);
    try {
      // Match the exact parameter names expected by the backend
      await projectService.addRepository(projectId, {
        githubRepoId: repoInfo.id,
        repoFullName: repoInfo.fullName,
        repoName: repoInfo.name,
        repoUrl: repoUrl,
        cloneUrl: repoInfo.cloneUrl,
        defaultBranch: repoBranch || repoInfo.defaultBranch,
      });
      
      toast.success('Repository added successfully');
      onSuccess();
      onClose();
      
      // Reset form
      setRepoUrl('');
      setRepoBranch('main');
      setRepoValid(false);
      setRepoInfo(null);
      
    } catch (err: any) {
      console.error('Add repository error:', err);
      toast.error(err.response?.data?.message || 'Failed to add repository');
    } finally {
      setLoading(false);
    }
  };

  if (!isOpen) return null;

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

              {/* Repository URL Input */}
              <div className="mb-4">
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Repository URL *
                </label>
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={repoUrl}
                    onChange={(e) => {
                      setRepoUrl(e.target.value);
                      setRepoValid(false);
                      setRepoInfo(null);
                    }}
                    className="flex-1 px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-blue-500 focus:border-blue-500"
                    placeholder="https://github.com/username/repository"
                  />
                  <button
                    type="button"
                    onClick={handleValidateRepo}
                    disabled={validating || !repoUrl}
                    className="px-4 py-2 bg-gray-600 text-white rounded-md hover:bg-gray-700 disabled:opacity-50"
                  >
                    {validating ? '...' : 'Validate'}
                  </button>
                </div>
                {repoValid && repoInfo && (
                  <p className="text-green-600 text-sm mt-1">
                    ✓ {repoInfo.fullName}
                  </p>
                )}
              </div>

              {/* Branch (optional) */}
              <div className="mb-4">
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Branch (optional)
                </label>
                <input
                  type="text"
                  value={repoBranch}
                  onChange={(e) => setRepoBranch(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-blue-500 focus:border-blue-500"
                  placeholder="main"
                />
                <p className="text-xs text-gray-500 mt-1">
                  Default: {repoInfo?.defaultBranch || 'master'}
                </p>
              </div>
            </div>
          </div>

          <div className="bg-gray-50 px-4 py-3 sm:px-6 sm:flex sm:flex-row-reverse">
            <button
              onClick={handleAdd}
              disabled={loading || !repoValid}
              className="w-full inline-flex justify-center rounded-md border border-transparent shadow-sm px-4 py-2 bg-blue-600 text-base font-medium text-white hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 sm:ml-3 sm:w-auto sm:text-sm disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? 'Adding...' : 'Add Repository'}
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