import React from 'react';

export interface TemporaryChatIconProps {
  size?: number;
  className?: string;
}

/** Dashed circular speech bubble — ephemeral / temporary chat. */
const TemporaryChatIcon: React.FC<TemporaryChatIconProps> = ({
  size = 20,
  className,
}) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    aria-hidden
    className={className}
  >
    <circle
      cx="12"
      cy="11"
      r="7.5"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeDasharray="4 3.5"
    />
    <path
      d="M7 16.2 4.5 19.5 9 17.4"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeDasharray="4 3.5"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

export default TemporaryChatIcon;
