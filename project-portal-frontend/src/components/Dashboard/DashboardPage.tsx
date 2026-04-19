// src/components/Dashboard/DashboardPage.tsx
import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { projectService } from '../../services/project.service';
import { Project } from '../../types';
import ProjectCard from './ProjectCard';
import StatsWidget from './StatsWidget';
import LoadingSpinner from '../Common/LoadingSpinner';
import toast from 'react-hot-toast';

const DashboardPage: React.FC = () => {
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({
    totalProjects: 0,
    totalRepositories: 0,
    totalVulnerabilities: 0,
    criticalVulnerabilities: 0
  });

  useEffect(() => {
    fetchProjects();
  }, []);

  const fetchProjects = async () => {
    try {
      setLoading(true);
      const data = await projectService.getProjects();
      setProjects(data);
      
      // Calculate statistics
      const totalRepos = data.reduce((sum, p) => sum + (p.repositories?.length || 0), 0);
      const totalVulns = data.reduce((sum, p) => {
        const projectVulns = p.repositories?.reduce((repoSum, repo) => 
          repoSum + (repo.vulnerabilityCount || 0), 0) || 0;
        return sum + projectVulns;
      }, 0);
      
      const criticalVulns = data.reduce((sum, p) => {
        const projectCritical = p.repositories?.reduce((repoSum, repo) => 
          repoSum + (repo.criticalCount || 0), 0) || 0;
        return sum + projectCritical;
      }, 0);
      
      setStats({
        totalProjects: data.length,
        totalRepositories: totalRepos,
        totalVulnerabilities: totalVulns,
        criticalVulnerabilities: criticalVulns
      });
    } catch (error) {
      console.error('Failed to fetch projects:', error);
      toast.error('Failed to load projects');
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return <LoadingSpinner />;
  }

  return (
    <div className="p-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-800">Dashboard</h1>
        <p className="text-gray-500 mt-1">Overview of your projects and security status</p>
      </div>

      {/* Stats Widgets */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
        <StatsWidget
          title="Total Projects"
          value={stats.totalProjects}
          icon="projects"
          color="blue"
        />
        <StatsWidget
          title="Repositories"
          value={stats.totalRepositories}
          icon="repositories"
          color="green"
        />
        <StatsWidget
          title="Vulnerabilities"
          value={stats.totalVulnerabilities}
          icon="vulnerabilities"
          color="red"
        />
        <StatsWidget
          title="Critical Issues"
          value={stats.criticalVulnerabilities}
          icon="critical"
          color="orange"
        />
      </div>

      {/* Quick Actions */}
      <div className="bg-white rounded-lg shadow-sm p-6 mb-8">
        <h2 className="text-lg font-semibold mb-4">Quick Actions</h2>
        <div className="flex gap-4">
          <Link
            to="/projects/new"
            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition"
          >
            + Create New Project
          </Link>
          <Link
            to="/projects"
            className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition"
          >
            View All Projects
          </Link>
        </div>
      </div>

      {/* Recent Projects */}
      <div>
        <div className="flex justify-between items-center mb-4">
          <h2 className="text-xl font-semibold">Recent Projects</h2>
          <Link to="/projects" className="text-blue-600 hover:text-blue-700 text-sm">
            View all →
          </Link>
        </div>
        
        {projects.length === 0 ? (
          <div className="bg-white rounded-lg shadow-sm p-12 text-center">
            <svg className="mx-auto h-12 w-12 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
            </svg>
            <h3 className="mt-2 text-sm font-medium text-gray-900">No projects</h3>
            <p className="mt-1 text-sm text-gray-500">Get started by creating a new project.</p>
            <div className="mt-6">
              <Link
                to="/projects/new"
                className="inline-flex items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700"
              >
                Create New Project
              </Link>
            </div>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {projects.slice(0, 6).map((project) => (
              <ProjectCard key={project.id} project={project} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default DashboardPage;