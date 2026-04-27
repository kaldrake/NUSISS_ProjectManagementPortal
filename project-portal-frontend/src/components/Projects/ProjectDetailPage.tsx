// src/components/Projects/ProjectDetailPage.tsx
import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { projectService } from '../../services/project.service';
import { Project, Repository } from '../../types';
import RepositoryList from './RepositoryList';
import VulnerabilityList from './VulnerabilityList';
import AddRepositoryModal from './AddRepositoryModal';
import LoadingSpinner from '../Common/LoadingSpinner';
import ErrorAlert from '../Common/ErrorAlert';

type TabType = 'repositories' | 'vulnerabilities' | 'settings';

const ProjectDetailPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const [project, setProject] = useState<Project | null>(null);
  const [repositories, setRepositories] = useState<Repository[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<TabType>('repositories');
  const [isAddRepoModalOpen, setIsAddRepoModalOpen] = useState(false);

  // Wrap fetchProjectData in useCallback to fix dependency warning
  const fetchProjectData = useCallback(async () => {
    if (!projectId) return;
    
    try {
      setLoading(true);
      setError(null);
      // const [projectData, reposData] = await Promise.all([
      const [projectData] = await Promise.all([
        projectService.getProject(projectId),
        // projectService.getRepositories(projectId),
      ]);
      setProject(projectData);
      // setRepositories(reposData);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to load project');
      toast.error('Failed to load project');
    } finally {
      setLoading(false);
    }
  }, [projectId]); // Add projectId as dependency

  useEffect(() => {
    fetchProjectData();
  }, [fetchProjectData]); // Add fetchProjectData to dependency array

  const handleDeleteProject = async () => {
    const userConfirmed = window.confirm(`Are you sure you want to delete "${project?.name}"? This will remove all repositories and scan data.`);
    
    if (!userConfirmed) return;
    
    try {
      await projectService.deleteProject(projectId!);
      toast.success('Project deleted successfully');
      navigate('/projects');
    } catch (err) {
      toast.error('Failed to delete project');
    }
  };

  const handleRepositoryAdded = () => {
    fetchProjectData();
    setIsAddRepoModalOpen(false);
    toast.success('Repository added successfully');
  };

  const handleScanTriggered = () => {
    fetchProjectData();
  };

  if (loading) {
    return <LoadingSpinner />;
  }

  if (error || !project) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-8">
        <ErrorAlert message={error || 'Project not found'} onRetry={fetchProjectData} />
      </div>
    );
  }

  const tabs: { id: TabType; label: string; icon: React.ReactElement }[] = [
    {
      id: 'repositories',
      label: 'Repositories',
      icon: (
        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
        </svg>
      ),
    },
    {
      id: 'vulnerabilities',
      label: 'Vulnerabilities',
      icon: (
        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
        </svg>
      ),
    },
    {
      id: 'settings',
      label: 'Settings',
      icon: (
        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
      ),
    },
  ];

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="mb-6">
        <button
          onClick={() => navigate('/projects')}
          className="text-gray-500 hover:text-gray-700 mb-2 flex items-center gap-1"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
          </svg>
          Back to Projects
        </button>
        <h1 className="text-3xl font-bold text-gray-800">{project.name}</h1>
        {project.description && (
          <p className="text-gray-500 mt-1">{project.description}</p>
        )}
      </div>

      {/* Tabs */}
      <div className="border-b border-gray-200 mb-6">
        <nav className="flex space-x-8">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-2 py-2 px-1 border-b-2 text-sm font-medium transition-colors ${
                activeTab === tab.id
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              }`}
            >
              {tab.icon}
              {tab.label}
            </button>
          ))}
        </nav>
      </div>

      {/* Tab Content */}
      <div>
        {activeTab === 'repositories' && (
          <RepositoryList
            repositories={repositories}
            onAddRepo={() => setIsAddRepoModalOpen(true)}
            onScanTriggered={handleScanTriggered}
            onRefresh={fetchProjectData}
          />
        )}
        
        {activeTab === 'vulnerabilities' && (
          <VulnerabilityList repositories={repositories} />
        )}
        
        {activeTab === 'settings' && (
          <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
            <h3 className="text-lg font-semibold text-gray-800 mb-4">Project Settings</h3>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Project ID</label>
                <code className="text-sm bg-gray-100 px-2 py-1 rounded">{project.id}</code>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Created</label>
                <p className="text-gray-600">{new Date(project.createdAt).toLocaleString()}</p>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Last Updated</label>
                <p className="text-gray-600">{new Date(project.updatedAt).toLocaleString()}</p>
              </div>
              <div className="pt-4 border-t border-gray-200">
                <button
                  onClick={handleDeleteProject}
                  className="bg-red-50 text-red-600 hover:bg-red-100 px-4 py-2 rounded-lg text-sm font-medium transition"
                >
                  Delete Project
                </button>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Add Repository Modal */}
      <AddRepositoryModal
        isOpen={isAddRepoModalOpen}
        onClose={() => setIsAddRepoModalOpen(false)}
        onSuccess={handleRepositoryAdded}
        projectId={projectId!}
        existingRepos={repositories}
      />
    </div>
  );
};

export default ProjectDetailPage;