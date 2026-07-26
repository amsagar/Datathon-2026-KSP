import React from 'react';
import CustomModal from '@atoms/CustomModal';
import AppearancePanel from '@molecules/AppearancePanel';

export interface AppearanceModalProps {
  open: boolean;
  onClose: () => void;
}

const AppearanceModal: React.FC<AppearanceModalProps> = ({ open, onClose }) => (
  <CustomModal open={open} title="Appearance" onClose={onClose} width="sm">
    <AppearancePanel />
  </CustomModal>
);

export default AppearanceModal;
