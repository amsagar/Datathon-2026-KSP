import React, { useState } from 'react';
import { motion, useReducedMotion } from 'motion/react';
import {
  Lock, Eye, EyeOff, Loader2, AlertCircle, CheckCircle2, ShieldAlert,
} from 'lucide-react';
import KspLogo from '@atoms/KspLogo';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { authApi } from '@apiCalls/services';
import { clearAuthToken } from '@apiCalls/auth';
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

const serverError = (err: unknown): string | null =>
  (err as { response?: { data?: { error?: string } } })?.response?.data?.error ||
  null;

const signOut = () => {
  clearAuthToken();
  window.location.href = '/login';
};

const ForcePasswordChangePage: React.FC = () => {
  const reduceMotion = useReducedMotion();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showNew, setShowNew] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentPassword) {
      setError('Enter your temporary password.');
      return;
    }
    if (newPassword.length < 8) {
      setError('New password must be at least 8 characters.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('New password and confirmation do not match.');
      return;
    }
    setError(null);
    setLoading(true);
    try {
      await authApi.changePassword({ currentPassword, newPassword });
      setDone(true);
      // The current JWT still carries mustChangePassword; force a fresh login
      // so the new (flag-free) token is minted.
      setTimeout(() => {
        clearAuthToken();
        window.location.href = '/login';
      }, 1600);
    } catch (err) {
      setError(serverError(err) || 'Could not update your password. Please try again.');
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
        <div className={`${bg.corner} ${bg.cornerTl}`} />
        <div className={`${bg.corner} ${bg.cornerBr}`} />
        <div className={bg.noise} />
        <div className={bg.vignette} />
      </div>

      <motion.div
        initial={reduceMotion ? false : { opacity: 0, y: 28, scale: 0.97 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.55, ease: [0.16, 1, 0.3, 1] }}
        className="relative z-10 mx-auto my-auto w-full max-w-md px-4"
      >
        <div className="overflow-hidden rounded-2xl border border-white/70 bg-white/96 shadow-[0_28px_80px_rgba(0,0,0,0.45),0_0_0_1px_rgba(255,255,255,0.08)_inset] backdrop-blur-xl dark:border-white/10 dark:bg-[#1f1e1c]/95">
          <motion.div
            className="h-1.5 w-full origin-left bg-gradient-to-r from-[#c9962b] via-[#b01722] to-[#c9962b]"
            initial={reduceMotion ? false : { scaleX: 0 }}
            animate={{ scaleX: 1 }}
            transition={{ delay: 0.2, duration: 0.7, ease: [0.16, 1, 0.3, 1] }}
          />

          <div className="px-8 pb-8 pt-7">
            <div className="mb-7 flex flex-col items-center text-center">
              <motion.div
                initial={reduceMotion ? false : { opacity: 0, scale: 0.75 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ delay: 0.12, duration: 0.45, ease: [0.16, 1, 0.3, 1] }}
                className="drop-shadow-[0_8px_20px_rgba(176,23,34,0.25)]"
              >
                <KspLogo size={96} />
              </motion.div>

              <motion.h1
                initial={reduceMotion ? false : { opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.22, duration: 0.4 }}
                className="mt-3 bg-gradient-to-r from-[#8f101a] via-[#b01722] to-[#c9962b] bg-clip-text text-xl font-semibold tracking-tight text-transparent"
              >
                Set a new password
              </motion.h1>
              <motion.p
                initial={reduceMotion ? false : { opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: 0.3, duration: 0.4 }}
                className="mt-1.5 text-sm text-muted-foreground"
              >
                Your account uses a temporary password. Choose a new one to continue.
              </motion.p>
            </div>

            {done ? (
              <motion.div
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                className="flex flex-col items-center gap-3 py-4 text-center"
              >
                <CheckCircle2 className="size-12 text-green-600" />
                <p className="text-sm font-medium text-foreground">
                  Password updated
                </p>
                <p className="text-sm text-muted-foreground">
                  Redirecting you to sign in with your new password…
                </p>
                <Loader2 className="mt-1 size-4 animate-spin text-muted-foreground" />
              </motion.div>
            ) : (
              <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
                <div className="flex items-start gap-2 rounded-md border border-[#c9962b]/40 bg-[#c9962b]/10 px-3 py-2 text-sm text-[#8a6a1d] dark:text-[#d8b25a]">
                  <ShieldAlert className="mt-0.5 size-4 shrink-0" />
                  <span>
                    For your security, you must change the temporary password
                    before using the platform.
                  </span>
                </div>

                <motion.div
                  custom={0}
                  variants={fieldVariants}
                  initial={reduceMotion ? false : 'hidden'}
                  animate="visible"
                  className="flex flex-col gap-1.5"
                >
                  <Label htmlFor="fpc-current">Temporary password</Label>
                  <div className="group relative">
                    <Lock className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground transition-colors group-focus-within:text-[#b01722]" />
                    <Input
                      id="fpc-current"
                      type="password"
                      autoComplete="current-password"
                      autoFocus
                      value={currentPassword}
                      onChange={(e) => setCurrentPassword(e.target.value)}
                      placeholder="Temporary password"
                      className="h-11 pl-9 transition-shadow focus-visible:ring-[#b01722]/30"
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
                  <Label htmlFor="fpc-new">New password</Label>
                  <div className="group relative">
                    <Lock className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground transition-colors group-focus-within:text-[#b01722]" />
                    <Input
                      id="fpc-new"
                      type={showNew ? 'text' : 'password'}
                      autoComplete="new-password"
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                      placeholder="At least 8 characters"
                      className="h-11 pl-9 pr-10 transition-shadow focus-visible:ring-[#b01722]/30"
                    />
                    <button
                      type="button"
                      onClick={() => setShowNew((v) => !v)}
                      aria-label={showNew ? 'Hide password' : 'Show password'}
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded-md p-1.5 text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground"
                    >
                      {showNew ? (
                        <EyeOff className="size-4" />
                      ) : (
                        <Eye className="size-4" />
                      )}
                    </button>
                  </div>
                </motion.div>

                <motion.div
                  custom={2}
                  variants={fieldVariants}
                  initial={reduceMotion ? false : 'hidden'}
                  animate="visible"
                  className="flex flex-col gap-1.5"
                >
                  <Label htmlFor="fpc-confirm">Confirm new password</Label>
                  <div className="group relative">
                    <Lock className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground transition-colors group-focus-within:text-[#b01722]" />
                    <Input
                      id="fpc-confirm"
                      type={showNew ? 'text' : 'password'}
                      autoComplete="new-password"
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      placeholder="Re-enter new password"
                      className="h-11 pl-9 transition-shadow focus-visible:ring-[#b01722]/30"
                    />
                  </div>
                </motion.div>

                {error && (
                  <motion.div
                    initial={{ opacity: 0, y: -6 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive"
                  >
                    <AlertCircle className="size-4 shrink-0" />
                    <span>{error}</span>
                  </motion.div>
                )}

                <motion.div
                  custom={3}
                  variants={fieldVariants}
                  initial={reduceMotion ? false : 'hidden'}
                  animate="visible"
                >
                  <Button
                    type="submit"
                    disabled={loading}
                    className={cn(
                      'mt-1 h-11 w-full text-base font-semibold shadow-lg shadow-[#b01722]/30',
                      'bg-gradient-to-r from-[#b01722] to-[#8f101a]',
                      'transition-all duration-200 hover:from-[#c01a26] hover:to-[#a01218] hover:shadow-xl hover:shadow-[#b01722]/40',
                      'active:scale-[0.98]',
                    )}
                  >
                    {loading && <Loader2 className="size-4 animate-spin" />}
                    Set new password
                  </Button>
                </motion.div>

                <div className="text-center">
                  <button
                    type="button"
                    onClick={signOut}
                    className="text-sm font-medium text-muted-foreground underline-offset-4 transition-colors hover:text-foreground hover:underline"
                  >
                    Sign out
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      </motion.div>
    </div>
  );
};

export default ForcePasswordChangePage;
