import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import CustomIcon from '@atoms/CustomIcon';
import CustomAvatar from '@atoms/CustomAvatar';
import CustomDropdown from '@atoms/CustomDropdown';
import AppearanceModal from '@organisms/AppearanceModal';
import { settingsPath } from '@constants/routePaths';
import { clearAuthToken, redirectToSso, isAdmin, getRoles } from '@apiCalls/auth';
import { useT } from '@constants/translations';
import * as styles from '@styles/accountMenu.module.scss';

export interface AccountMenuProps {
  displayName: string;
  email?: string;
  photoUrl?: string | null;
  initials: string;
  collapsed?: boolean;
}

const AccountMenu: React.FC<AccountMenuProps> = ({
  displayName,
  email,
  photoUrl,
  initials,
  collapsed,
}) => {
  const navigate = useNavigate();
  const [appearanceOpen, setAppearanceOpen] = useState(false);
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const admin = isAdmin();
  const roles = getRoles();
  const t = useT();
  const roleLabel = (role: string): string => {
    switch (role.toUpperCase()) {
      case 'ADMIN':
        return t('roleAdmin');
      case 'SUPERVISOR':
        return t('roleSupervisor');
      case 'INVESTIGATOR':
        return t('roleInvestigator');
      case 'ANALYST':
        return t('roleAnalyst');
      case 'POLICYMAKER':
        return t('rolePolicymaker');
      default:
        return t('roleUser');
    }
  };
  // Prefer ADMIN, else the first assigned role.
  const primaryRole = admin
    ? t('roleAdmin')
    : roles.length > 0
      ? roleLabel(roles[0])
      : t('roleUser');

  const goTo = (path: string) => {
    setDropdownOpen(false);
    navigate(path);
  };

  const handleLogout = () => {
    clearAuthToken();
    redirectToSso();
  };

  const menu = (
    <div className={styles.menuPanel}>
      <button
        type="button"
        className={styles.menuItem}
        onClick={() => {
          setDropdownOpen(false);
          setAppearanceOpen(true);
        }}
      >
        <CustomIcon name="appearance" size={15} />
        {t('appearance')}
      </button>
      <button
        type="button"
        className={styles.menuItem}
        onClick={() => goTo(settingsPath('profile'))}
      >
        <CustomIcon name="profile" size={15} />
        {t('myProfile')}
      </button>
      {admin && (
        <button
          type="button"
          className={styles.menuItem}
          onClick={() => goTo(settingsPath('assistants'))}
        >
          <CustomIcon name="settings" size={15} />
          {t('settings')}
        </button>
      )}
      <div className={styles.menuDivider} />
      <button
        type="button"
        className={`${styles.menuItem} ${styles.menuItemDanger}`}
        onClick={handleLogout}
      >
        <CustomIcon name="logout" size={15} />
        {t('logOut')}
      </button>
    </div>
  );

  const trigger = collapsed ? (
    <button type="button" className={styles.trigger} aria-label={t('accountMenu')}>
      <CustomAvatar
        size={32}
        src={photoUrl || undefined}
        style={{ backgroundColor: 'var(--primary, #b01722)', flexShrink: 0, fontSize: 13 }}
      >
        {initials}
      </CustomAvatar>
    </button>
  ) : (
    <button type="button" className={styles.trigger} aria-label={t('accountMenu')}>
      <CustomAvatar
        size={32}
        src={photoUrl || undefined}
        style={{ backgroundColor: 'var(--primary, #b01722)', flexShrink: 0, fontSize: 13 }}
      >
        {initials}
      </CustomAvatar>
      <span className={styles.triggerInfo}>
        <span className={styles.nameRow}>
          <span className={styles.triggerName}>{displayName}</span>
          <span
            className={`${styles.roleBadge} ${admin ? styles.roleBadgeAdmin : ''}`}
          >
            {primaryRole}
          </span>
        </span>
        {email && <span className={styles.triggerEmail}>{email}</span>}
      </span>
      <CustomIcon name="caret-down" size={10} className={styles.triggerChevron} />
    </button>
  );

  return (
    <>
      <CustomDropdown
        overlay={menu}
        trigger={['click']}
        placement="topRight"
        open={dropdownOpen}
        onOpenChange={setDropdownOpen}
      >
        {trigger}
      </CustomDropdown>
      <AppearanceModal
        open={appearanceOpen}
        onClose={() => setAppearanceOpen(false)}
      />
    </>
  );
};

export default AccountMenu;
