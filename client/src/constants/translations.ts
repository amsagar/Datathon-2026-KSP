import { useLangStore } from '@store/useLangStore';

/** UI string dictionary. Keys are English; kn holds the Kannada rendering. */
export const STRINGS = {
  // Sidebar
  brand: { en: 'Karnataka Police', kn: 'ಕರ್ನಾಟಕ ಪೊಲೀಸ್' },
  newChat: { en: 'New chat', kn: 'ಹೊಸ ಸಂಭಾಷಣೆ' },
  recent: { en: 'Recent', kn: 'ಇತ್ತೀಚಿನವು' },
  archived: { en: 'Archived', kn: 'ಆರ್ಕೈವ್' },
  noRecentChats: { en: 'No recent chats.', kn: 'ಇತ್ತೀಚಿನ ಸಂಭಾಷಣೆಗಳಿಲ್ಲ.' },
  noArchivedChats: { en: 'No archived chats.', kn: 'ಆರ್ಕೈವ್ ಮಾಡಿದ ಸಂಭಾಷಣೆಗಳಿಲ್ಲ.' },
  startOneToGetGoing: { en: 'Start a new one to get going.', kn: 'ಪ್ರಾರಂಭಿಸಲು ಹೊಸದೊಂದನ್ನು ರಚಿಸಿ.' },
  loadingChats: { en: 'Loading chats…', kn: 'ಸಂಭಾಷಣೆಗಳನ್ನು ಲೋಡ್ ಮಾಡಲಾಗುತ್ತಿದೆ…' },

  // Session date groups
  Today: { en: 'Today', kn: 'ಇಂದು' },
  Yesterday: { en: 'Yesterday', kn: 'ನಿನ್ನೆ' },
  'Previous 7 Days': { en: 'Previous 7 Days', kn: 'ಕಳೆದ 7 ದಿನಗಳು' },
  'Previous 30 Days': { en: 'Previous 30 Days', kn: 'ಕಳೆದ 30 ದಿನಗಳು' },
  Older: { en: 'Older', kn: 'ಹಳೆಯವು' },

  // Chat header
  style: { en: 'Style', kn: 'ಶೈಲಿ' },
  assistant: { en: 'Assistant', kn: 'ಸಹಾಯಕ' },
  model: { en: 'Model', kn: 'ಮಾದರಿ' },
  defaultStyle: { en: 'Default style', kn: 'ಡೀಫಾಲ್ಟ್ ಶೈಲಿ' },
  selectAssistant: { en: 'Select assistant', kn: 'ಸಹಾಯಕರನ್ನು ಆಯ್ಕೆಮಾಡಿ' },

  // Chat empty state
  howCanIHelp: { en: 'How can I help you today?', kn: 'ಇಂದು ನಾನು ನಿಮಗೆ ಹೇಗೆ ಸಹಾಯ ಮಾಡಲಿ?' },
  pickAssistant: {
    en: 'Pick an assistant and ask anything — your message will start a new chat.',
    kn: 'ಸಹಾಯಕರನ್ನು ಆಯ್ಕೆಮಾಡಿ ಮತ್ತು ಏನನ್ನಾದರೂ ಕೇಳಿ — ನಿಮ್ಮ ಸಂದೇಶವು ಹೊಸ ಸಂಭಾಷಣೆಯನ್ನು ಪ್ರಾರಂಭಿಸುತ್ತದೆ.',
  },
  sendToContinue: {
    en: 'Send a message to continue this chat.',
    kn: 'ಈ ಸಂಭಾಷಣೆಯನ್ನು ಮುಂದುವರಿಸಲು ಸಂದೇಶ ಕಳುಹಿಸಿ.',
  },

  // Composer
  messagePlaceholder: {
    en: 'Message Crime Intelligence…',
    kn: 'ಅಪರಾಧ ಗುಪ್ತಚರಕ್ಕೆ ಸಂದೇಶ…',
  },
  listening: { en: 'Listening… speak now', kn: 'ಕೇಳಿಸಿಕೊಳ್ಳುತ್ತಿದೆ… ಈಗ ಮಾತನಾಡಿ' },
  composerHint: {
    en: 'Press Enter to send · Shift + Enter for new line',
    kn: 'ಕಳುಹಿಸಲು Enter · ಹೊಸ ಸಾಲಿಗೆ Shift + Enter',
  },

  // Account menu
  appearance: { en: 'Appearance', kn: 'ಗೋಚರತೆ' },
  settings: { en: 'Settings', kn: 'ಸೆಟ್ಟಿಂಗ್‌ಗಳು' },
  crimeAnalytics: { en: 'Crime Analytics', kn: 'ಅಪರಾಧ ವಿಶ್ಲೇಷಣೆ' },
  usage: { en: 'Usage', kn: 'ಬಳಕೆ' },
  logOut: { en: 'Log out', kn: 'ಲಾಗ್ ಔಟ್' },

  // Login
  loginSubtitle: {
    en: 'Conversational AI · Networks · Forecasts · Evidence',
    kn: 'ಸಂವಾದಾತ್ಮಕ AI · ಜಾಲಗಳು · ಮುನ್ಸೂಚನೆಗಳು · ಸಾಕ್ಷ್ಯ',
  },
  search: { en: 'Search…', kn: 'ಹುಡುಕಿ…' },
  roleAdmin: { en: 'Admin', kn: 'ನಿರ್ವಾಹಕ' },
  roleSupervisor: { en: 'Supervisor', kn: 'ಮೇಲ್ವಿಚಾರಕ' },
  roleInvestigator: { en: 'Investigator', kn: 'ಅನ್ವೇಷಕ' },
  roleAnalyst: { en: 'Analyst', kn: 'ವಿಶ್ಲೇಷಕ' },
  rolePolicymaker: { en: 'Policymaker', kn: 'ನೀತಿ ನಿರ್ಮಾಪಕ' },
  roleUser: { en: 'User', kn: 'ಬಳಕೆದಾರ' },
  inLast90Days: { en: 'in last 90 days', kn: 'ಕಳೆದ 90 ದಿನಗಳಲ್ಲಿ' },
  timesUsual: { en: '× usual ~', kn: '× ಸಾಮಾನ್ಯ ~' },
  loginTitle: { en: 'Crime Intelligence Platform', kn: 'ಅಪರಾಧ ಗುಪ್ತಚರ ವೇದಿಕೆ' },
  username: { en: 'Username', kn: 'ಬಳಕೆದಾರ ಹೆಸರು' },
  password: { en: 'Password', kn: 'ಪಾಸ್‌ವರ್ಡ್' },
  signIn: { en: 'Sign in', kn: 'ಸೈನ್ ಇನ್' },
  enterUsername: { en: 'Enter your username', kn: 'ನಿಮ್ಮ ಬಳಕೆದಾರ ಹೆಸರನ್ನು ನಮೂದಿಸಿ' },
  enterPassword: { en: 'Enter your password', kn: 'ನಿಮ್ಮ ಪಾಸ್‌ವರ್ಡ್ ನಮೂದಿಸಿ' },
  invalidCreds: { en: 'Invalid username or password', kn: 'ಅಮಾನ್ಯ ಬಳಕೆದಾರ ಹೆಸರು ಅಥವಾ ಪಾಸ್‌ವರ್ಡ್' },
  loginFailed: { en: 'Login failed. Please try again.', kn: 'ಲಾಗಿನ್ ವಿಫಲವಾಗಿದೆ. ದಯವಿಟ್ಟು ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ.' },

  // Analytics nav + pages
  chat: { en: 'Chat', kn: 'ಸಂಭಾಷಣೆ' },
  dashboard: { en: 'Dashboard', kn: 'ಡ್ಯಾಶ್‌ಬೋರ್ಡ್' },
  hotspotMap: { en: 'Hotspot Map', kn: 'ಹಾಟ್‌ಸ್ಪಾಟ್ ನಕ್ಷೆ' },
  criminalNetwork: { en: 'Criminal Network', kn: 'ಅಪರಾಧ ಜಾಲ' },
  offenderRisk: { en: 'Offender Risk', kn: 'ಅಪರಾಧಿ ಅಪಾಯ' },
  financialCrime: { en: 'Financial Crime', kn: 'ಆರ್ಥಿಕ ಅಪರಾಧ' },
  crimeDashboard: { en: 'Crime Dashboard', kn: 'ಅಪರಾಧ ಡ್ಯಾಶ್‌ಬೋರ್ಡ್' },
  crimeHotspotMap: { en: 'Crime Hotspot Map', kn: 'ಅಪರಾಧ ಹಾಟ್‌ಸ್ಪಾಟ್ ನಕ್ಷೆ' },
  criminalNetworkAnalysis: { en: 'Criminal Network Analysis', kn: 'ಅಪರಾಧ ಜಾಲ ವಿಶ್ಲೇಷಣೆ' },
  offenderRiskProfiling: { en: 'Offender Risk Profiling', kn: 'ಅಪರಾಧಿ ಅಪಾಯ ವಿಶ್ಲೇಷಣೆ' },
  allDistricts: { en: 'All districts', kn: 'ಎಲ್ಲಾ ಜಿಲ್ಲೆಗಳು' },
  allCrimeHeads: { en: 'All crime heads', kn: 'ಎಲ್ಲಾ ಅಪರಾಧ ವಿಭಾಗಗಳು' },
  totalCases: { en: 'Total cases', kn: 'ಒಟ್ಟು ಪ್ರಕರಣಗಳು' },
  heinousCases: { en: 'Heinous cases', kn: 'ಘೋರ ಪ್ರಕರಣಗಳು' },
  districtsReporting: { en: 'Districts reporting', kn: 'ವರದಿ ಮಾಡುವ ಜಿಲ್ಲೆಗಳು' },
  earlyWarnings: { en: 'Early warnings — emerging crime patterns', kn: 'ಮುನ್ಸೂಚನೆಗಳು — ಉದಯೋನ್ಮುಖ ಅಪರಾಧ ಮಾದರಿಗಳು' },
  districtSummary: { en: 'District summary', kn: 'ಜಿಲ್ಲಾ ಸಾರಾಂಶ' },
  district: { en: 'District', kn: 'ಜಿಲ್ಲೆ' },
  stations: { en: 'Stations', kn: 'ಠಾಣೆಗಳು' },
  heinous: { en: 'Heinous', kn: 'ಘೋರ' },

  // Dashboard cards
  monthlyCaseVolume: { en: 'Monthly case volume', kn: 'ಮಾಸಿಕ ಪ್ರಕರಣ ಪ್ರಮಾಣ' },
  topCrimeHeadsRange: { en: 'Top crime heads across the selected range', kn: 'ಆಯ್ದ ಅವಧಿಯ ಪ್ರಮುಖ ಅಪರಾಧ ವಿಭಾಗಗಳು' },
  forecastTitle: { en: 'Forecast — next 6 months', kn: 'ಮುನ್ಸೂಚನೆ — ಮುಂದಿನ 6 ತಿಂಗಳು' },
  forecastDesc: { en: 'Projected monthly case volume over the selected scope', kn: 'ಆಯ್ದ ವ್ಯಾಪ್ತಿಯಲ್ಲಿ ನಿರೀಕ್ಷಿತ ಮಾಸಿಕ ಪ್ರಕರಣ ಪ್ರಮಾಣ' },
  accusedByAge: { en: 'Accused by age band', kn: 'ವಯಸ್ಸಿನ ಆಧಾರದ ಮೇಲೆ ಆರೋಪಿಗಳು' },
  demographicsDesc: { en: 'Aggregate demographic profile of accused (age/gender only; caste & religion excluded)', kn: 'ಆರೋಪಿಗಳ ಒಟ್ಟು ಜನಸಂಖ್ಯಾ ವಿವರ (ವಯಸ್ಸು/ಲಿಂಗ ಮಾತ್ರ; ಜಾತಿ ಮತ್ತು ಧರ್ಮ ಹೊರತುಪಡಿಸಲಾಗಿದೆ)' },
  loadingTrends: { en: 'Loading trends…', kn: 'ಪ್ರವೃತ್ತಿಗಳನ್ನು ಲೋಡ್ ಮಾಡಲಾಗುತ್ತಿದೆ…' },
  noTrendData: { en: 'No trend data for this selection.', kn: 'ಈ ಆಯ್ಕೆಗೆ ಪ್ರವೃತ್ತಿ ಡೇಟಾ ಇಲ್ಲ.' },
  lookHereFirst: { en: 'look here first', kn: 'ಮೊದಲು ಇಲ್ಲಿ ನೋಡಿ' },
  predictedHotspots: { en: 'Predicted hotspots — next quarter', kn: 'ಮುನ್ಸೂಚಿತ ಹಾಟ್‌ಸ್ಪಾಟ್‌ಗಳು — ಮುಂದಿನ ತ್ರೈಮಾಸಿಕ' },
  predictedHotspotsDesc: { en: 'Districts ranked by forecast case volume vs. their own recent trend', kn: 'ಸ್ವಂತ ಇತ್ತೀಚಿನ ಪ್ರವೃತ್ತಿಗೆ ಹೋಲಿಸಿದರೆ ಮುನ್ಸೂಚಿತ ಪ್ರಕರಣ ಪ್ರಮಾಣದ ಆಧಾರದ ಮೇಲೆ ಜಿಲ್ಲೆಗಳ ಶ್ರೇಣಿ' },
  recentTotal: { en: 'Recent (last quarter)', kn: 'ಇತ್ತೀಚಿನ (ಕಳೆದ ತ್ರೈಮಾಸಿಕ)' },
  predictedTotal: { en: 'Predicted (next quarter)', kn: 'ಮುನ್ಸೂಚಿತ (ಮುಂದಿನ ತ್ರೈಮಾಸಿಕ)' },
  changeRatio: { en: 'Change', kn: 'ಬದಲಾವಣೆ' },
  forecastMape: { en: 'Backtest error (MAPE)', kn: 'ಬ್ಯಾಕ್‌ಟೆಸ್ಟ್ ದೋಷ (MAPE)' },

  // Network view
  coOffenderNetwork: { en: 'Co-offender network', kn: 'ಸಹ-ಆರೋಪಿ ಜಾಲ' },
  detectedGroups: { en: 'Detected organized groups', kn: 'ಪತ್ತೆಯಾದ ಸಂಘಟಿತ ಗುಂಪುಗಳು' },
  clusters: { en: 'clusters', kn: 'ಕ್ಲಸ್ಟರ್‌ಗಳು' },
  explore: { en: 'Explore', kn: 'ಅನ್ವೇಷಿಸಿ' },
  offenderIdPlaceholder: { en: 'Offender ID e.g. P000123 — empty = statewide', kn: 'ಅಪರಾಧಿ ಐಡಿ ಉದಾ. P000123 — ಖಾಲಿ = ರಾಜ್ಯವ್ಯಾಪಿ' },
  noNetworkFound: { en: 'No network found. Clear search for statewide pairs.', kn: 'ಯಾವುದೇ ಜಾಲ ಸಿಗಲಿಲ್ಲ. ರಾಜ್ಯವ್ಯಾಪಿ ಜೋಡಿಗಳಿಗೆ ಹುಡುಕಾಟ ತೆರವುಗೊಳಿಸಿ.' },
  focusThisPerson: { en: 'Focus this person →', kn: 'ಈ ವ್ಯಕ್ತಿಯನ್ನು ಕೇಂದ್ರೀಕರಿಸಿ →' },
  close: { en: 'Close', kn: 'ಮುಚ್ಚಿ' },
  mostConnected: { en: 'Most connected', kn: 'ಅತಿ ಹೆಚ್ಚು ಸಂಪರ್ಕ' },

  // Offenders view
  offenderRiskRanking: { en: 'Offender risk ranking', kn: 'ಅಪರಾಧಿ ಅಪಾಯ ಶ್ರೇಣೀಕರಣ' },
  tierHigh: { en: 'High', kn: 'ಹೆಚ್ಚು' },
  tierElevated: { en: 'Elevated', kn: 'ಏರಿಕೆ' },
  tierWatch: { en: 'Watch', kn: 'ಗಮನ' },
  noOffenders: { en: 'No offenders match the current risk model.', kn: 'ಪ್ರಸ್ತುತ ಅಪಾಯ ಮಾದರಿಗೆ ಯಾವುದೇ ಅಪರಾಧಿಗಳು ಹೊಂದಿಕೆಯಾಗುವುದಿಲ್ಲ.' },
  colRiskScore: { en: 'Risk score', kn: 'ಅಪಾಯ ಅಂಕ' },
  colName: { en: 'Name', kn: 'ಹೆಸರು' },
  colOffenderId: { en: 'Offender ID', kn: 'ಅಪರಾಧಿ ಐಡಿ' },
  colCases: { en: 'Cases', kn: 'ಪ್ರಕರಣಗಳು' },
  colChargesheeted: { en: 'Chargesheeted', kn: 'ದೋಷಾರೋಪಣೆ' },
  colLastCase: { en: 'Last case', kn: 'ಕೊನೆಯ ಪ್ರಕರಣ' },
  peopleSuffix: { en: 'people', kn: 'ಜನ' },

  // Financial view
  suspiciousTxns: { en: 'Suspicious & high-value transactions', kn: 'ಶಂಕಿತ ಮತ್ತು ಹೆಚ್ಚಿನ ಮೌಲ್ಯದ ವಹಿವಾಟುಗಳು' },
  muleAccountsTitle: { en: 'Fan-in ("mule") accounts — receiving from many sources', kn: 'ಫ್ಯಾನ್-ಇನ್ ("ಮ್ಯೂಲ್") ಖಾತೆಗಳು — ಹಲವು ಮೂಲಗಳಿಂದ ಸ್ವೀಕರಿಸುತ್ತಿವೆ' },
  traceMoneyTrailPlaceholder: { en: 'Trace money trail — offender ID e.g. P000123', kn: 'ಹಣದ ಜಾಡು ಪತ್ತೆ — ಅಪರಾಧಿ ಐಡಿ ಉದಾ. P000123' },
  trace: { en: 'Trace', kn: 'ಪತ್ತೆ' },
  moneyTrailFor: { en: 'Money trail for', kn: 'ಹಣದ ಜಾಡು' },
  transactionsWord: { en: 'transactions', kn: 'ವಹಿವಾಟುಗಳು' },
  colFrom: { en: 'From', kn: 'ಇಂದ' },
  colTo: { en: 'To', kn: 'ಗೆ' },
  colAmount: { en: 'Amount', kn: 'ಮೊತ್ತ' },
  colType: { en: 'Type', kn: 'ಪ್ರಕಾರ' },
  colDate: { en: 'Date', kn: 'ದಿನಾಂಕ' },
  colFlag: { en: 'Flag', kn: 'ಗುರುತು' },
  suspiciousBadge: { en: 'Suspicious', kn: 'ಶಂಕಿತ' },
  colAccountHolder: { en: 'Account holder', kn: 'ಖಾತೆದಾರ' },
  colAccount: { en: 'Account', kn: 'ಖಾತೆ' },
  colDistinctSources: { en: 'Distinct sources', kn: 'ವಿಭಿನ್ನ ಮೂಲಗಳು' },
  colIncomingTxns: { en: 'Incoming txns', kn: 'ಒಳಬರುವ ವಹಿವಾಟುಗಳು' },
  colTotalReceived: { en: 'Total received', kn: 'ಒಟ್ಟು ಸ್ವೀಕೃತ' },

  // Chat thread — tool cards, message actions, typing/loading states (previously hardcoded English
  // regardless of the UI language toggle)
  thinking: { en: 'Thinking…', kn: 'ಯೋಚಿಸುತ್ತಿದೆ…' },
  loadingConversation: { en: 'Loading conversation…', kn: 'ಸಂಭಾಷಣೆಯನ್ನು ಲೋಡ್ ಮಾಡಲಾಗುತ್ತಿದೆ…' },
  copy: { en: 'Copy', kn: 'ನಕಲಿಸಿ' },
  copied: { en: 'Copied', kn: 'ನಕಲಿಸಲಾಗಿದೆ' },
  editAndResend: { en: 'Edit & resend', kn: 'ಸಂಪಾದಿಸಿ ಮತ್ತು ಮರುಕಳುಹಿಸಿ' },
  edit: { en: 'Edit', kn: 'ಸಂಪಾದಿಸಿ' },
  resend: { en: 'Resend', kn: 'ಮರುಕಳುಹಿಸಿ' },
  copyReply: { en: 'Copy reply', kn: 'ಪ್ರತ್ಯುತ್ತರ ನಕಲಿಸಿ' },
  regenerate: { en: 'Regenerate', kn: 'ಮರುಸೃಷ್ಟಿಸಿ' },
  toolRunning: { en: 'Running…', kn: 'ಚಾಲನೆಯಲ್ಲಿದೆ…' },
  toolInput: { en: 'Input', kn: 'ಇನ್‌ಪುಟ್' },
  toolOutput: { en: 'Output', kn: 'ಔಟ್‌ಪುಟ್' },
  readAloud: { en: 'Read aloud', kn: 'ಗಟ್ಟಿಯಾಗಿ ಓದಿ' },
  stopReadingAloud: { en: 'Stop reading aloud', kn: 'ಓದುವುದನ್ನು ನಿಲ್ಲಿಸಿ' },
  pauseReadingAloud: { en: 'Pause', kn: 'ವಿರಾಮ' },
  resumeReadingAloud: { en: 'Resume', kn: 'ಮುಂದುವರಿಸಿ' },
  startVoiceInput: { en: 'Start voice input', kn: 'ಧ್ವನಿ ಇನ್‌ಪುಟ್ ಪ್ರಾರಂಭಿಸಿ' },
  stopVoiceInput: { en: 'Stop voice input', kn: 'ಧ್ವನಿ ಇನ್‌ಪುಟ್ ನಿಲ್ಲಿಸಿ' },
  acceptVoiceInput: { en: 'Use this transcript', kn: 'ಈ ಪಠ್ಯವನ್ನು ಬಳಸಿ' },
  rejectVoiceInput: { en: 'Discard transcript', kn: 'ಪಠ್ಯವನ್ನು ತ್ಯಜಿಸಿ' },
  voiceReviewHint: {
    en: 'Listening — accept or discard when ready',
    kn: 'ಕೇಳಿಸಿಕೊಳ್ಳುತ್ತಿದೆ — ಸಿದ್ಧರಾದಾಗ ಸ್ವೀಕರಿಸಿ ಅಥವಾ ತ್ಯಜಿಸಿ',
  },
  micPermissionDenied: { en: 'Microphone access was denied.', kn: 'ಮೈಕ್ರೊಫೋನ್ ಪ್ರವೇಶವನ್ನು ನಿರಾಕರಿಸಲಾಗಿದೆ.' },
  noSpeechDetected: { en: 'No speech was detected.', kn: 'ಯಾವುದೇ ಮಾತು ಪತ್ತೆಯಾಗಿಲ್ಲ.' },
  voiceNetworkError: { en: 'A network error interrupted voice input.', kn: 'ಜಾಲಬಂಧ ದೋಷವು ಧ್ವನಿ ಇನ್‌ಪುಟ್ ಅನ್ನು ಅಡ್ಡಿಪಡಿಸಿತು.' },
  voiceInputFailed: { en: 'Voice input failed.', kn: 'ಧ್ವನಿ ಇನ್‌ಪುಟ್ ವಿಫಲವಾಗಿದೆ.' },
  clarifyingQuestionsLabel: { en: 'Clarifying questions', kn: 'ಸ್ಪಷ್ಟೀಕರಣ ಪ್ರಶ್ನೆಗಳು' },
  clarifyingQuestionsTitle: { en: 'A few details will help me proceed', kn: 'ಕೆಲವು ವಿವರಗಳು ಮುಂದುವರಿಯಲು ಸಹಾಯ ಮಾಡುತ್ತವೆ' },
  orTypeOwnAnswer: { en: 'Or type your own answer', kn: 'ಅಥವಾ ನಿಮ್ಮ ಸ್ವಂತ ಉತ್ತರವನ್ನು ಟೈಪ್ ಮಾಡಿ' },
  continueLabel: { en: 'Continue', kn: 'ಮುಂದುವರಿಸಿ' },
  toolGroupWorking: { en: 'Working…', kn: 'ಕೆಲಸ ಮಾಡುತ್ತಿದೆ…' },
  toolGroupErrors: { en: 'Completed with errors', kn: 'ದೋಷಗಳೊಂದಿಗೆ ಪೂರ್ಣಗೊಂಡಿದೆ' },
  toolGroupCompleted: { en: 'Completed', kn: 'ಪೂರ್ಣಗೊಂಡಿದೆ' },
  toolGroupOneStep: { en: '1 step', kn: '1 ಹಂತ' },
  toolGroupSteps: { en: 'steps', kn: 'ಹಂತಗಳು' },
  exportChatToPdf: { en: 'Export chat to PDF', kn: 'ಸಂಭಾಷಣೆಯನ್ನು PDF ಗೆ ರಫ್ತು ಮಾಡಿ' },
  saveConversationAsPdf: { en: 'Save this conversation as a PDF', kn: 'ಈ ಸಂಭಾಷಣೆಯನ್ನು PDF ಆಗಿ ಉಳಿಸಿ' },
  translateToKannada: { en: 'Show in Kannada', kn: 'ಕನ್ನಡದಲ್ಲಿ ತೋರಿಸಿ' },
  translateToEnglish: { en: 'Show in English', kn: 'ಇಂಗ್ಲಿಷ್‌ನಲ್ಲಿ ತೋರಿಸಿ' },
  showOriginal: { en: 'Show original', kn: 'ಮೂಲವನ್ನು ತೋರಿಸಿ' },
  translatedLabel: { en: 'Translated', kn: 'ಅನುವಾದಿತ' },
  heyReady: { en: 'Ready when you are', kn: 'ನೀವು ಸಿದ್ಧರಾದಾಗ ಹೇಳಿ' },
  translateFailed: { en: 'Translation failed. Please try again.', kn: 'ಅನುವಾದ ವಿಫಲವಾಗಿದೆ. ದಯವಿಟ್ಟು ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ.' },
  ttsFailed: { en: 'Could not play Indian voice audio.', kn: 'ಭಾರತೀಯ ಧ್ವನಿ ಆಡಿಯೋ ಪ್ಲೇ ಮಾಡಲಾಗಲಿಲ್ಲ.' },
} as const;

export type StringKey = keyof typeof STRINGS;

/** Reactive translate hook — re-renders the caller when the language toggles. */
export function useT(): (key: StringKey) => string {
  const lang = useLangStore((s) => s.lang);
  return (key: StringKey) => STRINGS[key][lang] ?? STRINGS[key].en;
}
