// src/components/Common/SeverityBadge.tsx
import React from 'react';

interface SeverityBadgeProps {
  severity: 'BLOCKER' | 'CRITICAL' | 'MAJOR' | 'MINOR' | 'INFO';
}

const severityConfig = {
  BLOCKER: { label: 'Blocker', color: 'bg-red-600', textColor: 'text-red-600' },
  CRITICAL: { label: 'Critical', color: 'bg-red-500', textColor: 'text-red-500' },
  MAJOR: { label: 'Major', color: 'bg-orange-500', textColor: 'text-orange-500' },
  MINOR: { label: 'Minor', color: 'bg-yellow-500', textColor: 'text-yellow-600' },
  INFO: { label: 'Info', color: 'bg-blue-500', textColor: 'text-blue-500' },
};

const SeverityBadge: React.FC<SeverityBadgeProps> = ({ severity }) => {
  const config = severityConfig[severity];
  
  return (
    <span className={`inline-flex items-center px-2 py-1 rounded-full text-xs font-medium ${config.color} bg-opacity-10 ${config.textColor}`}>
      {config.label}
    </span>
  );
};

export default SeverityBadge;