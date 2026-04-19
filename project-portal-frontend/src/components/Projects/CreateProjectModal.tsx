// src/components/Projects/CreateProjectModal.tsx
import React, { useState } from 'react';
import toast from 'react-hot-toast';
import { projectService } from '../../services/project.service';
import { githubService } from '../../services/github.service';

interface CreateProjectModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const CreateProjectModal: React.FC<CreateProjectModalProps> = ({ isOpen, onClose, onSuccess }) => {
  const [projectName, setProjectName] = useState('');
  const [projectDescription, setProjectDescription] = useState('');
  const [repoUrl, setRepoUrl] = useState('');
  const [repoBranch, setRepoBranch] = useState('main');
  const [loading, setLoading] = useState(false);
  const [validatingRepo, setValidatingRepo] = useState(false);
  const [repoValid, setRepoValid] = useState(false);

  const handleValidateRepo = async () => {
    if (!repoUrl) {
      toast.error('Please enter a repository URL');
      return;
    }

    setValidatingRepo(true);
    try {
      const result = await githubService.validateRepoUrl(repoUrl);
      if (result.isValid) {
        setRepoValid(true);
        toast.success(`Repository "${result.repoName}" is valid!`);
      } else {
        setRepoValid(false);
        toast.error('Invalid repository URL');
      }
    } catch (error) {
      setRepoValid(false);
      toast.error('Failed to validate repository');
    } finally {
      setValidatingRepo(false);
    }
  };

  const handleSubmit = async () => {
    if (!projectName.trim()) {
      toast.error('Please enter a project name');
      return;
    }

    if (!repoUrl.trim()) {
      toast.error('Please enter a repository URL');
      return;
    }

    if (!repoValid) {
      toast.error('Please validate the repository URL first');
      return;
    }

    setLoading(true);
    try {
      // Create project
      const project = await projectService.createProject({
        name: projectName,
        description: projectDescription
      });

      // Link repository to project
      await githubService.linkRepository(project.id, {
        repoUrl: repoUrl,
        repoName: repoUrl.split('/').pop() || repoUrl,
        branch: repoBranch
      });

      toast.success(`Project "${projectName}" created with linked repository`);
      onSuccess();
      onClose();
      // Reset form
      setProjectName('');
      setProjectDescription('');
      setRepoUrl('');
      setRepoBranch('main');
      setRepoValid(false);
    } catch (error) {
      console.error('Failed to create project:', error);
      toast.error('Failed to create project');
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
                Create New Project
              </h3>

              {/* Project Name */}
              <div className="mb-4">
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Project Name *
                </label>
                <input
                  type="text"
                  value={projectName}
                  onChange={(e) => setProjectName(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-blue-500 focus:border-blue-500"
                  placeholder="e.g., E-Commerce Security Scan"
                />
              </div>

              {/* Description */}
              <div className="mb-4">
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Description
                </label>
                <textarea
                  value={projectDescription}
                  onChange={(e) => setProjectDescription(e.target.value)}
                  rows={3}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-blue-500 focus:border-blue-500"
                  placeholder="Brief description of the project..."
                />
              </div>

              {/* GitHub Repository URL */}
              <div className="mb-4">
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  GitHub Repository URL *
                </label>
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={repoUrl}
                    onChange={(e) => {
                      setRepoUrl(e.target.value);
                      setRepoValid(false);
                    }}
                    className="flex-1 px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-blue-500 focus:border-blue-500"
                    placeholder="https://github.com/username/repo"
                  />
                  <button
                    type="button"
                    onClick={handleValidateRepo}
                    disabled={validatingRepo || !repoUrl}
                    className="px-4 py-2 bg-gray-600 text-white rounded-md hover:bg-gray-700 disabled:opacity-50"
                  >
                    {validatingRepo ? '...' : 'Validate'}
                  </button>
                </div>
                {repoValid && (
                  <p className="text-green-600 text-sm mt-1">✓ Repository is valid and accessible</p>
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
                <p className="text-xs text-gray-500 mt-1">Default: main</p>
              </div>
            </div>
          </div>

          <div className="bg-gray-50 px-4 py-3 sm:px-6 sm:flex sm:flex-row-reverse">
            <button
              onClick={handleSubmit}
              disabled={loading || !repoValid || !projectName.trim()}
              className="w-full inline-flex justify-center rounded-md border border-transparent shadow-sm px-4 py-2 bg-blue-600 text-base font-medium text-white hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 sm:ml-3 sm:w-auto sm:text-sm disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? 'Creating...' : 'Create Project'}
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

export default CreateProjectModal;