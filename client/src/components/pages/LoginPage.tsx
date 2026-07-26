import React, { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, useReducedMotion } from 'motion/react';
import {
  AlertCircle,
  Eye,
  EyeOff,
  Loader2,
  Lock,
  User,
} from 'lucide-react';
import axios from 'axios';
import { ROUTE_PATHS } from '@constants/routePaths';
import KspLogo from '@atoms/KspLogo';
import { useLangStore } from '@store/useLangStore';
import { useT } from '@constants/translations';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import {
  decodeJwt,
  setAuthToken,
  setAuthUser,
  takePostLoginRedirect,
} from '@apiCalls/auth';
import * as bg from '@styles/loginPage.module.scss';

const fieldVariants = {
  hidden: { opacity: 0, y: 12 },
  visible: (i: number) => ({
    opacity: 1,
    y: 0,
    transition: {
      delay: 0.28 + i * 0.08,
      duration: 0.4,
      ease: [0.16, 1, 0.3, 1] as [number, number, number, number],
    },
  }),
};

/** Compact capability rail — platform pillars without card clutter */
const CAPABILITIES = [
  'Conversational AI',
  'Criminal networks',
  'Pattern & hotspots',
  'Socio-demographics',
  'Offender profiling',
  'Case decision support',
  'Financial trails',
  'Early-warning forecasts',
  'Explainable evidence',
  'Role-based governance',
];

const UI_FONT = 'Poppins, -apple-system, BlinkMacSystemFont, sans-serif';

const labelProps = {
  fill: 'rgba(255,248,240,0.55)',
  fontSize: 10,
  fontFamily: UI_FONT,
  fontWeight: 700,
  letterSpacing: 2.2,
} as const;

/** Intelligence fabric: every challenge pillar hinted as living graphics */
const AmbientScene = () => (
  <svg
    className={bg.scene}
    viewBox="0 0 1440 900"
    preserveAspectRatio="xMidYMid slice"
    aria-hidden
  >
    <defs>
      <radialGradient id="glowY" cx="50%" cy="50%" r="50%">
        <stop offset="0%" stopColor="#ffcc00" stopOpacity="0.45" />
        <stop offset="100%" stopColor="#ffcc00" stopOpacity="0" />
      </radialGradient>
      <radialGradient id="glowR" cx="50%" cy="50%" r="50%">
        <stop offset="0%" stopColor="#e31c25" stopOpacity="0.4" />
        <stop offset="100%" stopColor="#e31c25" stopOpacity="0" />
      </radialGradient>
    </defs>

    {/* Mesh */}
    <g opacity="0.12">
      {Array.from({ length: 12 }).map((_, i) => (
        <line
          key={`h${i}`}
          x1="0"
          y1={50 + i * 72}
          x2="1440"
          y2={50 + i * 72}
          stroke="#ffcc00"
          strokeWidth="0.6"
        />
      ))}
      {Array.from({ length: 18 }).map((_, i) => (
        <line
          key={`v${i}`}
          x1={20 + i * 82}
          y1="0"
          x2={20 + i * 82}
          y2="900"
          stroke="#e31c25"
          strokeWidth="0.5"
        />
      ))}
    </g>

    {/* 1. Conversational AI — ask → answer */}
    <g className={bg.driftA}>
      <text x="1080" y="118" textAnchor="middle" {...labelProps}>
        ASK THE DATABASE
      </text>
      <g className={bg.chatPulse}>
        <rect
          x="1000"
          y="138"
          width="168"
          height="54"
          rx="16"
          fill="rgba(255,204,0,0.16)"
          stroke="#ffcc00"
          strokeWidth="1.2"
        />
        <text
          x="1084"
          y="160"
          textAnchor="middle"
          fill="#fff8f0"
          fontSize="11"
          fontFamily={UI_FONT}
        >
          “Show co-accused for
        </text>
        <text
          x="1084"
          y="178"
          textAnchor="middle"
          fill="#fff8f0"
          fontSize="11"
          fontFamily={UI_FONT}
        >
          P000184…”
        </text>
      </g>
      <g className={bg.chatPulse} style={{ animationDelay: '1.1s' }}>
        <rect
          x="1035"
          y="208"
          width="140"
          height="46"
          rx="14"
          fill="rgba(227,28,37,0.2)"
          stroke="#e31c25"
          strokeWidth="1.2"
        />
        <text
          x="1105"
          y="228"
          textAnchor="middle"
          fill="#ffe9a8"
          fontSize="10"
          fontFamily={UI_FONT}
        >
          3 FIRs · 5 linked
        </text>
        <text
          x="1105"
          y="244"
          textAnchor="middle"
          fill="#ffe9a8"
          fontSize="10"
          fontFamily={UI_FONT}
        >
          accused · EN / ಕನ್ನಡ
        </text>
      </g>
      {/* Voice */}
      <circle
        cx="1228"
        cy="188"
        r="18"
        fill="none"
        stroke="rgba(255,204,0,0.45)"
        strokeWidth="1.2"
        className={bg.pulseNode}
      />
      <circle
        cx="1228"
        cy="188"
        r="28"
        fill="none"
        stroke="rgba(255,204,0,0.22)"
        strokeWidth="1"
        className={bg.pulseNodeDelay}
      />
      <circle cx="1228" cy="188" r="6" fill="#ffcc00" />
      <text x="1228" y="228" textAnchor="middle" fill="rgba(255,204,0,0.55)" fontSize="8" fontWeight="700">
        VOICE
      </text>
    </g>

    {/* 2. Criminal network */}
    <g className={bg.driftB}>
      <text x="920" y="318" textAnchor="middle" {...labelProps}>
        CRIME NET
      </text>
      <path
        d="M1084 254 L920 360"
        stroke="rgba(255,204,0,0.45)"
        strokeWidth="1.2"
        className={bg.drawLink}
      />
      <path
        d="M760 330 L920 360"
        stroke="#e31c25"
        strokeWidth="1.5"
        className={`${bg.drawLink} ${bg.drawLinkDelay}`}
      />
      <path d="M920 360 L1040 400" stroke="#e31c25" strokeWidth="1.4" className={bg.drawLink} />
      <path
        d="M920 360 L840 460"
        stroke="rgba(255,204,0,0.65)"
        strokeWidth="1.3"
        className={`${bg.drawLink} ${bg.drawLinkDelay}`}
      />
      <path d="M1040 400 L1140 480" stroke="#e31c25" strokeWidth="1.3" className={bg.drawLink} />
      <path
        d="M840 460 L1040 400"
        stroke="rgba(255,204,0,0.45)"
        strokeWidth="1.2"
        className={`${bg.drawLink} ${bg.drawLinkDelay}`}
      />
      <circle cx="760" cy="330" r="8" fill="#fff9ef" stroke="#e31c25" strokeWidth="1.5" className={bg.pulseNode} />
      <circle
        cx="920"
        cy="360"
        r="13"
        fill="#ffcc00"
        stroke="#e31c25"
        strokeWidth="1.8"
        className={bg.pulseNodeDelay}
      />
      <circle cx="1040" cy="400" r="8" fill="#fff9ef" stroke="#e31c25" strokeWidth="1.4" />
      <circle cx="840" cy="460" r="7" fill="#fff9ef" stroke="#ffcc00" strokeWidth="1.4" className={bg.pulseNode} />
      <circle
        cx="1140"
        cy="480"
        r="8"
        fill="#fff9ef"
        stroke="#e31c25"
        strokeWidth="1.4"
        className={bg.pulseNodeDelay}
      />
      <g className={bg.orbitRing} style={{ transformOrigin: '920px 360px' }}>
        <ellipse
          cx="920"
          cy="360"
          rx="56"
          ry="24"
          fill="none"
          stroke="rgba(255,204,0,0.28)"
          strokeWidth="1"
          strokeDasharray="4 7"
        />
      </g>
    </g>

    {/* 3. FIR stream */}
    <g className={bg.driftC}>
      <text x="700" y="168" textAnchor="middle" {...labelProps}>
        FIR STREAM
      </text>
      <g transform="translate(640 180) rotate(-8)">
        <rect width="64" height="80" rx="5" fill="rgba(255,248,240,0.72)" />
        <rect width="64" height="13" rx="5" fill="#e31c25" />
        <rect x="9" y="28" width="44" height="3.5" rx="1" fill="rgba(30,12,8,0.22)" />
        <rect x="9" y="38" width="34" height="3.5" rx="1" fill="rgba(30,12,8,0.16)" />
        <rect x="9" y="48" width="40" height="3.5" rx="1" fill="rgba(30,12,8,0.16)" />
      </g>
      <g transform="translate(710 218) rotate(6)">
        <rect width="56" height="70" rx="4" fill="rgba(255,248,240,0.55)" />
        <rect width="56" height="11" rx="4" fill="#b0141c" />
      </g>
      <g transform="translate(770 195) rotate(-3)" opacity="0.45">
        <rect width="48" height="60" rx="4" fill="rgba(255,248,240,0.5)" />
        <rect width="48" height="10" rx="4" fill="#e31c25" />
      </g>
      <path
        d="M704 260 L760 330"
        stroke="rgba(227,28,37,0.45)"
        strokeWidth="1.1"
        className={bg.drawLink}
      />
    </g>

    {/* 4. Hotspots */}
    <g className={bg.driftB}>
      <text x="220" y="430" textAnchor="middle" {...labelProps}>
        HOTSPOTS
      </text>
      <ellipse cx="220" cy="510" rx="95" ry="62" fill="url(#glowR)" className={bg.pulseNode} />
      <ellipse cx="290" cy="555" rx="58" ry="42" fill="url(#glowY)" className={bg.pulseNodeDelay} />
      <circle cx="210" cy="500" r="5" fill="#e31c25" />
      <circle cx="255" cy="530" r="7" fill="#ffcc00" className={bg.pulseNode} />
      <circle cx="305" cy="560" r="4" fill="#e31c25" />
      <circle cx="175" cy="550" r="3.5" fill="#ffcc00" />
      <path
        d="M255 530 L760 330"
        stroke="rgba(255,204,0,0.18)"
        strokeWidth="1"
        strokeDasharray="4 8"
        className={bg.drawLink}
      />
    </g>

    {/* 5. Sociological / demographic bars */}
    <g className={bg.driftA} opacity="0.85">
      <text x="180" y="680" textAnchor="middle" {...labelProps}>
        SOCIO INSIGHTS
      </text>
      <rect x="110" y="700" width="14" height="48" rx="2" fill="rgba(255,204,0,0.55)" />
      <rect x="132" y="718" width="14" height="30" rx="2" fill="rgba(227,28,37,0.55)" />
      <rect x="154" y="708" width="14" height="40" rx="2" fill="rgba(255,255,255,0.35)" />
      <rect x="176" y="690" width="14" height="58" rx="2" fill="rgba(255,204,0,0.4)" className={bg.pulseNode} />
      <rect x="198" y="712" width="14" height="36" rx="2" fill="rgba(227,28,37,0.4)" />
      <rect x="220" y="702" width="14" height="46" rx="2" fill="rgba(255,255,255,0.28)" />
    </g>

    {/* 6. Offender profiling ring */}
    <g className={bg.driftC}>
      <text x="480" y="300" textAnchor="middle" {...labelProps}>
        RISK PROFILE
      </text>
      <circle cx="480" cy="360" r="36" fill="none" stroke="rgba(255,204,0,0.35)" strokeWidth="1.2" />
      <circle
        cx="480"
        cy="360"
        r="26"
        fill="none"
        stroke="rgba(227,28,37,0.45)"
        strokeWidth="1.4"
        strokeDasharray="8 6"
        className={bg.orbitRing}
        style={{ transformOrigin: '480px 360px' }}
      />
      <circle cx="480" cy="360" r="8" fill="#ffcc00" className={bg.pulseNode} />
      <text
        x="480"
        y="400"
        textAnchor="middle"
        fill="rgba(255,248,240,0.5)"
        fontSize="9"
        fontWeight="700"
      >
        RISK 0.86
      </text>
    </g>

    {/* 7. Financial trail */}
    <g className={bg.driftC} opacity="0.8">
      <text x="380" y="140" textAnchor="middle" {...labelProps}>
        MONEY TRAIL
      </text>
      <path
        d="M160 180 C240 140, 300 220, 380 165 C460 120, 520 210, 600 160"
        fill="none"
        stroke="rgba(255,204,0,0.5)"
        strokeWidth="1.5"
        strokeDasharray="6 8"
        className={bg.drawLink}
      />
      <circle cx="200" cy="170" r="5" fill="#ffcc00" className={bg.pulseNode} />
      <circle cx="380" cy="165" r="5" fill="#e31c25" className={bg.pulseNodeDelay} />
      <circle cx="560" cy="170" r="5" fill="#ffcc00" />
      <rect
        x="350"
        y="178"
        width="60"
        height="18"
        rx="4"
        fill="rgba(0,0,0,0.25)"
        stroke="rgba(255,204,0,0.35)"
      />
      <text
        x="380"
        y="191"
        textAnchor="middle"
        fill="#ffe9a8"
        fontSize="8"
        fontFamily={UI_FONT}
      >
        ₹ → ₹ → ₹
      </text>
    </g>

    {/* 8. Forecast / early warning */}
    <g className={`${bg.driftA} ${bg.wave}`}>
      <text x="980" y="620" textAnchor="middle" {...labelProps}>
        EARLY WARNING
      </text>
      <path
        d="M560 720 C640 680, 720 760, 820 700 C900 660, 980 740, 1080 690 C1160 660, 1240 720, 1340 680"
        fill="none"
        stroke="rgba(255,204,0,0.35)"
        strokeWidth="1.5"
      />
      <path
        d="M560 740 L620 740 L660 680 L740 740 L820 640 L900 740 L980 670 L1060 740 L1140 690 L1220 740 L1320 660"
        fill="none"
        stroke="#e31c25"
        strokeWidth="2"
        strokeLinejoin="round"
        className={`${bg.drawLink} ${bg.drawLinkDelay}`}
      />
      <circle cx="820" cy="640" r="8" fill="#ffcc00" className={bg.pulseNode} />
      <circle cx="1140" cy="690" r="6" fill="#e31c25" className={bg.pulseNodeDelay} />
    </g>

    {/* 9. Explainable evidence path */}
    <g className={bg.driftB} opacity="0.75">
      <text x="1280" y="560" textAnchor="middle" {...labelProps}>
        EVIDENCE PATH
      </text>
      <path
        d="M1140 480 L1220 540 L1280 520 L1340 580"
        fill="none"
        stroke="rgba(255,255,255,0.35)"
        strokeWidth="1.3"
        strokeDasharray="3 5"
        className={bg.drawLink}
      />
      <circle cx="1220" cy="540" r="4" fill="#fff" />
      <circle cx="1280" cy="520" r="4" fill="#ffcc00" />
      <circle cx="1340" cy="580" r="5" fill="#e31c25" className={bg.pulseNode} />
    </g>
  </svg>
);

const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const reduceMotion = useReducedMotion();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const lang = useLangStore((s) => s.lang);
  const toggleLang = useLangStore((s) => s.toggle);
  const t = useT();

  const titleParts = useMemo(() => {
    const full = t('loginTitle');
    if (lang === 'en') {
      return { a: 'Crime Intelligence', b: ' Platform' };
    }
    const parts = full.split(/\s+/).filter(Boolean);
    if (parts.length >= 2) {
      return {
        a: parts.slice(0, Math.ceil(parts.length / 2)).join(' '),
        b: ` ${parts.slice(Math.ceil(parts.length / 2)).join(' ')}`,
      };
    }
    return { a: full, b: '' };
  }, [t, lang]);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim()) {
      setError(t('enterUsername'));
      return;
    }
    if (!password) {
      setError(t('enterPassword'));
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const { data } = await axios.post<{ token: string }>(
        '/api/v1/auth/login',
        { username, password },
      );
      const decoded = decodeJwt(data.token);
      if (!decoded || !decoded.sub) {
        setError(t('loginFailed'));
        return;
      }
      setAuthToken(data.token);
      setAuthUser({
        upn: decoded.sub,
        name: typeof decoded.name === 'string' ? decoded.name : undefined,
        email: typeof decoded.email === 'string' ? decoded.email : undefined,
      });
      navigate(takePostLoginRedirect() || ROUTE_PATHS.CHAT, { replace: true });
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        setError(t('invalidCreds'));
      } else {
        setError(t('loginFailed'));
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={bg.page}>
      <div className={bg.bg} aria-hidden>
        <div className={bg.flagBands} />
        {!reduceMotion && <div className={bg.flagSheen} />}
        <div className={bg.veil} />
        <AmbientScene />
        <div className={`${bg.corner} ${bg.cornerTl}`} />
        <div className={`${bg.corner} ${bg.cornerBr}`} />
        <div className={bg.noise} />
        <div className={bg.vignette} />
      </div>

      <motion.button
        type="button"
        onClick={toggleLang}
        initial={reduceMotion ? false : { opacity: 0, y: -8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.25, duration: 0.4 }}
        whileHover={reduceMotion ? undefined : { scale: 1.04 }}
        whileTap={reduceMotion ? undefined : { scale: 0.97 }}
        className={bg.langBtn}
      >
        {lang === 'en' ? 'ಕನ್ನಡ' : 'English'}
      </motion.button>

      <div className={bg.shell}>
        <motion.aside
          className={bg.mission}
          initial={reduceMotion ? false : { opacity: 0, x: -18 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.55, ease: [0.16, 1, 0.3, 1] }}
        >
          <div className={bg.missionEyebrow}>Karnataka State Police</div>
          <h1 className={bg.missionBrand}>
            <span className={bg.missionBrandA}>{titleParts.a}</span>
            <span className={bg.missionBrandB}>{titleParts.b}</span>
          </h1>
          <p className={bg.missionTag}>
            Ask the crime database.
            <span> See the hidden links.</span>
          </p>
          <p className={bg.missionLead}>
            Natural-language intelligence over FIRs, accused, victims, locations, and
            socio-economic patterns — so investigators, analysts, and policymakers can
            discover networks, forecast risk, and act with explainable evidence.
          </p>
          <div className={bg.capRail} aria-label="Platform capabilities">
            {CAPABILITIES.map((cap) => (
              <span key={cap} className={bg.capChip}>
                {cap}
              </span>
            ))}
          </div>
          <p className={bg.missionFoot}>
            Beyond retrieval — pattern discovery · prevention · accountable AI
          </p>
        </motion.aside>

        <motion.div
          className={bg.cardWrap}
          initial={reduceMotion ? false : { opacity: 0, y: 24, scale: 0.98 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          transition={{ duration: 0.55, ease: [0.16, 1, 0.3, 1] }}
        >
          <div className={bg.card}>
            <motion.div
              className={bg.cardStripe}
              initial={reduceMotion ? false : { scaleX: 0 }}
              animate={{ scaleX: 1 }}
              transition={{ delay: 0.15, duration: 0.65, ease: [0.16, 1, 0.3, 1] }}
            />
            <div className={bg.cardBody}>
              <div className={bg.brandMark}>
                <motion.div
                  initial={reduceMotion ? false : { opacity: 0, scale: 0.8 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={{ delay: 0.1, duration: 0.4 }}
                  className="drop-shadow-[0_10px_28px_rgba(255,204,0,0.3)]"
                >
                  <KspLogo size={88} />
                </motion.div>
                <p className={bg.cardHeading}>Enter the workspace</p>
                <p className={bg.subtitle}>{t('loginSubtitle')}</p>
                <div className={bg.verifyRow}>
                  <span className={bg.verifyPill}>EN · ಕನ್ನಡ · Voice</span>
                  <span className={bg.verifyPill}>RBAC</span>
                  <span className={bg.verifyPill}>Audit trail</span>
                </div>
              </div>

              <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
                <motion.div
                  custom={0}
                  variants={fieldVariants}
                  initial={reduceMotion ? false : 'hidden'}
                  animate="visible"
                  className="flex flex-col gap-1.5"
                >
                  <Label htmlFor="username" className={bg.fieldLabel}>
                    {t('username')}
                  </Label>
                  <div className="group relative">
                    <User
                      className={`pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 ${bg.fieldIcon}`}
                    />
                    <Input
                      id="username"
                      autoFocus
                      autoComplete="username"
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      placeholder={t('username')}
                      className={`${bg.inputDark} pl-9`}
                    />
                  </div>
                </motion.div>

                <motion.div
                  custom={1}
                  variants={fieldVariants}
                  initial={reduceMotion ? false : 'hidden'}
                  animate="visible"
                  className="flex flex-col gap-1.5"
                >
                  <Label htmlFor="password" className={bg.fieldLabel}>
                    {t('password')}
                  </Label>
                  <div className="group relative">
                    <Lock
                      className={`pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 ${bg.fieldIcon}`}
                    />
                    <Input
                      id="password"
                      type={showPassword ? 'text' : 'password'}
                      autoComplete="current-password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder={t('password')}
                      className={`${bg.inputDark} pl-9 pr-10`}
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword((v) => !v)}
                      aria-label={showPassword ? 'Hide password' : 'Show password'}
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded-md p-1.5 text-[rgba(255,248,240,0.55)] transition-colors hover:bg-[rgba(255,204,0,0.12)] hover:text-[#ffcc00]"
                    >
                      {showPassword ? (
                        <EyeOff className="size-4" />
                      ) : (
                        <Eye className="size-4" />
                      )}
                    </button>
                  </div>
                </motion.div>

                {error && (
                  <motion.div
                    initial={{ opacity: 0, y: -6 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="flex items-center gap-2 rounded-md border border-red-400/40 bg-red-500/15 px-3 py-2 text-sm text-red-200"
                  >
                    <AlertCircle className="size-4 shrink-0" />
                    <span>{error}</span>
                  </motion.div>
                )}

                <motion.div
                  custom={2}
                  variants={fieldVariants}
                  initial={reduceMotion ? false : 'hidden'}
                  animate="visible"
                >
                  <Button type="submit" disabled={loading} className={bg.submitBtn}>
                    {loading && <Loader2 className="size-4 animate-spin" />}
                    {t('signIn')}
                  </Button>
                </motion.div>
              </form>

              <p className={bg.footerNote}>
                Karnataka State Police · Authorized personnel only
              </p>
            </div>
          </div>
        </motion.div>
      </div>
    </div>
  );
};

export default LoginPage;
