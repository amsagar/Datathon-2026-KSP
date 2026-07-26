import React, { useState } from 'react';
import CustomButton from '@atoms/CustomButton';
import CustomTextarea from '@atoms/CustomTextarea';
import * as styles from '@styles/chatThread.module.scss';

export interface MessageEditorProps {
  initialValue: string;
  disabled?: boolean;
  onSubmit: (text: string) => void;
  onCancel: () => void;
}

const MessageEditor: React.FC<MessageEditorProps> = ({
  initialValue,
  disabled,
  onSubmit,
  onCancel,
}) => {
  const [value, setValue] = useState(initialValue);

  const commit = () => {
    if (!value.trim()) return;
    onSubmit(value);
  };

  return (
    <div className={styles.editor}>
      <CustomTextarea
        value={value}
        autoFocus
        rows={3}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            commit();
          }
          if (e.key === 'Escape') onCancel();
        }}
        fullWidth
      />
      <div className={styles.editorActions}>
        <CustomButton variant="secondary" onClick={onCancel}>
          Cancel
        </CustomButton>
        <CustomButton
          variant="primary"
          onClick={commit}
          disabled={disabled || !value.trim()}
        >
          Save &amp; send
        </CustomButton>
      </div>
    </div>
  );
};

export default MessageEditor;
