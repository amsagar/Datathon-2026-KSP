import React from 'react';
import CustomIcon from '@atoms/CustomIcon';
import CustomTooltip from '@atoms/CustomTooltip';
import * as styles from '@styles/fieldLabel.module.scss';

export interface FieldLabelProps {
  label: React.ReactNode;
  info?: React.ReactNode;
  htmlFor?: string;
  /** Render label and control on the same row (default false = stacked). */
  inline?: boolean;
  children?: React.ReactNode;
}

const FieldLabel: React.FC<FieldLabelProps> = ({
  label,
  info,
  htmlFor,
  inline,
  children,
}) => (
  <div className={inline ? styles.wrapInline : styles.wrap}>
    <label className={styles.label} htmlFor={htmlFor}>
      <span>{label}</span>
      {info && (
        <CustomTooltip title={info} placement="bottom">
          <span className={styles.info} aria-label="More info">
            <CustomIcon name="info" size={12} />
          </span>
        </CustomTooltip>
      )}
    </label>
    {children}
  </div>
);

export default FieldLabel;
