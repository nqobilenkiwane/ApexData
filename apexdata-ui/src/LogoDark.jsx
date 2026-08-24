import React from 'react';

const LogoDark = ({ width = 400, height = 150 }) => {
  return (
    <svg
      viewBox="0 0 400 150"
      width={width}
      height={height}
      xmlns="http://www.w3.org/2000/svg"
    >
      {/* Chart Icon - White & Brand Blue */}
      <rect x="150" y="50" width="14" height="25" fill="#FFFFFF" rx="2" />
      <rect x="170" y="35" width="14" height="40" fill="#2563EB" rx="2" />
      <rect x="190" y="45" width="14" height="30" fill="#FFFFFF" rx="2" />
      <rect x="210" y="55" width="14" height="20" fill="#FFFFFF" rx="2" />
      <rect x="230" y="25" width="14" height="50" fill="#2563EB" rx="2" />

      {/* Brand Name (Spelling Corrected) */}
      <text
        x="200"
        y="110"
        fontFamily="'Inter', 'Segoe UI', sans-serif"
        fontSize="24"
        fontWeight="800"
        fill="#FFFFFF"
        textAnchor="middle"
        letterSpacing="3"
      >
        UNIVERSITY OF FOREX
      </text>

      {/* Tagline */}
      <text
        x="200"
        y="130"
        fontFamily="'Inter', 'Segoe UI', sans-serif"
        fontSize="11"
        fontWeight="600"
        fill="#888888"
        textAnchor="middle"
        letterSpacing="2"
      >
        FINANCIAL EDUCATION SIMPLIFIED
      </text>
    </svg>
  );
};

export default LogoDark;