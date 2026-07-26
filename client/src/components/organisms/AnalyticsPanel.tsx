import React from 'react';
import { useSearchParams } from 'react-router-dom';
import { motion } from 'motion/react';
import { hasRole, isAdmin } from '@apiCalls/auth';
import { StringKey } from '@constants/translations';
import CrimeDashboardView from '@pages/views/CrimeDashboardView';
import CrimeMapView from '@pages/views/CrimeMapView';
import CrimeNetworkView from '@pages/views/CrimeNetworkView';
import OffendersView from '@pages/views/OffendersView';
import FinancialView from '@pages/views/FinancialView';
import * as styles from '@styles/analyticsLayout.module.scss';

export type AnalyticsTab = 'dashboard' | 'map' | 'network' | 'offenders' | 'financial';

const TAB_META: {
  id: AnalyticsTab;
  key: StringKey;
  show: (roles: { investigative: boolean; analyst: boolean }) => boolean;
  help: string;
}[] = [
  {
    id: 'dashboard',
    key: 'dashboard',
    show: () => true,
    help: 'FIR overview — totals, early-warning spikes, trends, and district summary.',
  },
  {
    id: 'map',
    key: 'hotspotMap',
    show: () => true,
    help: 'Map of where FIRs landed. Colour = density, size = case count. Hover a spot for details.',
  },
  {
    id: 'network',
    key: 'criminalNetwork',
    show: ({ analyst }) => analyst,
    help: 'Who appears together in FIRs. Open a group (or search an ID). In a person web: gold = FIRs, red = co-accused. One Back button returns to groups.',
  },
  {
    id: 'offenders',
    key: 'offenderRisk',
    show: ({ investigative }) => investigative,
    help: 'Ranked accused by risk score. Click an Offender ID to open their network.',
  },
  {
    id: 'financial',
    key: 'financialCrime',
    show: ({ investigative }) => investigative,
    help: 'Suspicious/high-value transactions, fan-in "mule" accounts, and per-offender money trails.',
  },
];

/** Crime analytics hosted inside the chat window (not a separate full-page app). */
const AnalyticsPanel: React.FC = () => {
  const [params] = useSearchParams();
  const investigative = isAdmin() || hasRole('SUPERVISOR') || hasRole('INVESTIGATOR');
  const analyst = investigative || hasRole('ANALYST');
  const roles = { investigative, analyst };

  const tabs = TAB_META.filter((tab) => tab.show(roles));
  const raw = params.get('analytics') || 'dashboard';
  const active: AnalyticsTab = tabs.some((tab) => tab.id === raw)
    ? (raw as AnalyticsTab)
    : tabs[0]?.id || 'dashboard';

  return (
    <div className={`${styles.page} ${styles.embedded}`}>
      <motion.div
        key={active}
        className={styles.body}
        initial={{ opacity: 0, y: 6 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.28, ease: [0.16, 1, 0.3, 1] }}
      >
        {active === 'dashboard' && <CrimeDashboardView />}
        {active === 'map' && <CrimeMapView />}
        {active === 'network' && <CrimeNetworkView />}
        {active === 'offenders' && <OffendersView />}
        {active === 'financial' && <FinancialView />}
      </motion.div>
    </div>
  );
};

export default AnalyticsPanel;
