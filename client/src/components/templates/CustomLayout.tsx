import React from 'react';
import * as styles from '@styles/app.module.scss';

export interface CustomLayoutProps {
  children: React.ReactNode;
  style?: React.CSSProperties;
  className?: string;
}

const CustomLayout: React.FC<CustomLayoutProps> = ({
  children,
  style,
  className,
}) => (
  <div
    className={[styles.layout, className].filter(Boolean).join(' ')}
    style={style}
  >
    {children}
  </div>
);

export default CustomLayout;
