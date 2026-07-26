import React from 'react';
import kspLogo from '@assets/ksp-logo.png';

export interface KspLogoProps {
  size?: number;
  className?: string;
}

/** Official Karnataka State Police emblem. */
const KspLogo: React.FC<KspLogoProps> = ({ size, className }) => (
  <img
    src={kspLogo}
    width={size}
    height={size}
    className={className}
    alt="Karnataka State Police"
    style={{ objectFit: 'contain' }}
  />
);

export default KspLogo;
