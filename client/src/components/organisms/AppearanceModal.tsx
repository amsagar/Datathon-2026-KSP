import React from 'react';
import CustomModal from '@atoms/CustomModal';
import AppearancePanel from '@molecules/AppearancePanel';
import { useT } from '@constants/translations';

export interface AppearanceModalProps {
  open: boolean;
  onClose: () => void;
}

const AppearanceModal: React.FC<AppearanceModalProps> = ({ open, onClose }) => {
  const t = useT();
  return (
    <CustomModal open={open} title={t('appearanceTitle')} onClose={onClose} width="sm">
      <AppearancePanel />
    </CustomModal>
  );
};

export default AppearanceModal;
