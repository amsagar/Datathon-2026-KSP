import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { Camera, KeyRound, Save, UserRound } from 'lucide-react';
import CustomButton from '@atoms/CustomButton';
import CustomInput from '@atoms/CustomInput';
import CustomDatePicker from '@atoms/CustomDatePicker';
import CustomAvatar from '@atoms/CustomAvatar';
import CustomTag from '@atoms/CustomTag';
import CustomFileUpload from '@atoms/CustomFileUpload';
import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils';
import { authApi } from '@apiCalls/services';
import { useNotification } from '@providers/NotificationProviders';
import type { UserProfileResponse } from '@apiCalls/auth';
import type { ProfileUpdateRequest } from '@interfaces/user.interface';

/** Editable slice of the profile, mirrored into local form state. */
interface ProfileForm {
  displayName: string;
  email: string;
  phone: string;
  dateOfBirth: string;
  designation: string;
  department: string;
}

const EMPTY_FORM: ProfileForm = {
  displayName: '',
  email: '',
  phone: '',
  dateOfBirth: '',
  designation: '',
  department: '',
};

const toForm = (p: UserProfileResponse): ProfileForm => ({
  displayName: p.name ?? '',
  email: p.email ?? '',
  phone: p.phone ?? '',
  dateOfBirth: p.dateOfBirth ?? '',
  designation: p.designation ?? '',
  department: p.department ?? '',
});

const initials = (name: string | null, upn: string | null): string => {
  const base = (name?.trim() || upn?.trim() || '?').replace(/^@/, '');
  const parts = base.split(/\s+/).filter(Boolean);
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
  return base.slice(0, 2).toUpperCase();
};

const roleTone = (role: string): 'error' | 'info' | 'neutral' =>
  role === 'ADMIN' ? 'error' : role === 'ANALYST' ? 'neutral' : 'info';

const serverError = (err: unknown): string | null =>
  (err as { response?: { data?: { error?: string } } })?.response?.data?.error ||
  null;

const fieldLabel =
  'mb-1.5 block text-xs font-medium text-foreground';

const cardClass =
  'overflow-hidden rounded-2xl border border-border bg-card shadow-sm';

const cardHeaderClass =
  'flex items-center gap-2.5 border-b border-border bg-muted/30 px-5 py-3.5';

const cardBodyClass = 'px-5 py-5';

const cardFooterClass =
  'flex flex-wrap items-center gap-3 border-t border-border bg-muted/20 px-5 py-3.5';

const primaryBtnClass =
  'bg-gradient-to-r from-[#b01722] to-[#8f101a] text-white shadow-md shadow-[#b01722]/25 hover:from-[#c01a26] hover:to-[#a01218]';

const ProfilePage: React.FC = () => {
  const notify = useNotification();
  const reduceMotion = useReducedMotion();

  const [profile, setProfile] = useState<UserProfileResponse | null>(null);
  const [form, setForm] = useState<ProfileForm>(EMPTY_FORM);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [photoUrl, setPhotoUrl] = useState<string | undefined>();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [pwError, setPwError] = useState<string | null>(null);
  const [changingPw, setChangingPw] = useState(false);

  const loadPhoto = useCallback(async () => {
    try {
      const url = await authApi.photoObjectUrl();
      setPhotoUrl((prev) => {
        if (prev) URL.revokeObjectURL(prev);
        return url ?? undefined;
      });
    } catch {
      /* no photo — fall back to initials */
    }
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const me = await authApi.me();
      setProfile(me);
      setForm(toForm(me));
    } catch (err) {
      notify(serverError(err) || 'Failed to load profile', 'Error');
    } finally {
      setLoading(false);
    }
  }, [notify]);

  useEffect(() => {
    void load();
    void loadPhoto();
  }, [load, loadPhoto]);

  useEffect(
    () => () => {
      if (photoUrl) URL.revokeObjectURL(photoUrl);
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  const isDirty = useMemo(() => {
    if (!profile) return false;
    const saved = toForm(profile);
    return (Object.keys(saved) as (keyof ProfileForm)[]).some(
      (k) => saved[k] !== form[k],
    );
  }, [profile, form]);

  const handleUpload = async (file: File | null) => {
    if (!file) return;
    setUploading(true);
    try {
      await authApi.uploadPhoto(file);
      await loadPhoto();
      notify('Photo updated', 'Success');
    } catch (err) {
      notify(serverError(err) || 'Failed to upload photo', 'Error');
    } finally {
      setUploading(false);
    }
  };

  const saveProfile = async () => {
    setSaving(true);
    try {
      const patch: ProfileUpdateRequest = {
        displayName: form.displayName.trim(),
        email: form.email.trim(),
        phone: form.phone.trim(),
        dateOfBirth: form.dateOfBirth || undefined,
        designation: form.designation.trim(),
        department: form.department.trim(),
      };
      const updated = await authApi.updateMe(patch);
      setProfile(updated);
      setForm(toForm(updated));
      notify('Profile updated', 'Success');
    } catch (err) {
      notify(serverError(err) || 'Failed to save changes', 'Error');
    } finally {
      setSaving(false);
    }
  };

  const changePassword = async () => {
    setPwError(null);
    if (newPassword.length < 8) {
      setPwError('New password must be at least 8 characters.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setPwError('New password and confirmation do not match.');
      return;
    }
    setChangingPw(true);
    try {
      await authApi.changePassword({ currentPassword, newPassword });
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      notify('Password changed', 'Success');
    } catch (err) {
      setPwError(serverError(err) || 'Failed to change password.');
    } finally {
      setChangingPw(false);
    }
  };

  const displayName = profile?.name?.trim() || profile?.upn || 'Your account';
  const setField =
    (key: keyof ProfileForm) => (e: React.ChangeEvent<HTMLInputElement>) =>
      setForm((f) => ({ ...f, [key]: e.target.value }));

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden bg-background">
      <header className="flex-shrink-0 border-b border-border px-6 pb-4 pt-5 sm:px-8">
        <div className="text-[11px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
          Account
        </div>
        <h1 className="mt-1 m-0 text-2xl font-semibold tracking-tight text-foreground">
          My profile
        </h1>
        <p className="mt-1.5 max-w-xl text-sm leading-relaxed text-muted-foreground">
          Update your personal details and manage your sign-in password.
        </p>
      </header>

      <div className="min-h-0 flex-1 overflow-auto px-6 py-6 sm:px-8">
        <motion.div
          initial={reduceMotion ? false : { opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
          className="mx-auto flex w-full max-w-4xl flex-col gap-6"
        >
          {/* Profile card */}
          <section className={cardClass}>
            <div className={cardHeaderClass}>
              <span className="flex size-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <UserRound className="size-4" aria-hidden />
              </span>
              <div>
                <h2 className="m-0 text-sm font-semibold text-foreground">
                  Profile information
                </h2>
                <p className="m-0 text-xs text-muted-foreground">
                  How you appear across Crime Intelligence
                </p>
              </div>
            </div>

            <div className={cardBodyClass}>
              {loading ? (
                <div className="flex flex-col gap-6">
                  <div className="flex items-center gap-4">
                    <Skeleton className="size-[88px] rounded-full" />
                    <div className="flex-1 space-y-2.5">
                      <Skeleton className="h-5 w-48" />
                      <Skeleton className="h-3.5 w-36" />
                      <Skeleton className="h-6 w-24 rounded-full" />
                    </div>
                  </div>
                  <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                    {Array.from({ length: 6 }).map((_, i) => (
                      <Skeleton key={i} className="h-16 w-full rounded-lg" />
                    ))}
                  </div>
                </div>
              ) : (
                <>
                  <div className="flex flex-col gap-5 rounded-xl border border-border bg-muted/25 p-4 sm:flex-row sm:items-center">
                    <div className="relative shrink-0 self-center sm:self-auto">
                      <CustomAvatar
                        src={photoUrl}
                        size={88}
                        alt={displayName}
                        style={{
                          backgroundColor: 'var(--primary, #b01722)',
                          fontSize: 28,
                          fontWeight: 600,
                        }}
                      >
                        {initials(profile?.name ?? null, profile?.upn ?? null)}
                      </CustomAvatar>
                      <span className="absolute -bottom-1 -right-1 flex size-7 items-center justify-center rounded-full border-2 border-card bg-primary text-primary-foreground shadow-sm">
                        <Camera className="size-3.5" aria-hidden />
                      </span>
                    </div>

                    <div className="min-w-0 flex-1 text-center sm:text-left">
                      <div className="flex flex-wrap items-center justify-center gap-2 sm:justify-start">
                        <span className="text-lg font-semibold tracking-tight text-foreground">
                          {displayName}
                        </span>
                        {profile?.admin && (
                          <CustomTag
                            tone="error"
                            className="border-transparent bg-primary/12 text-primary"
                          >
                            Admin
                          </CustomTag>
                        )}
                      </div>
                      <div className="mt-0.5 font-mono text-[13px] text-muted-foreground">
                        {profile?.upn ? `@${profile.upn}` : '—'}
                      </div>
                      <div className="mt-3 flex flex-wrap items-center justify-center gap-1.5 sm:justify-start">
                        {profile && profile.roles.length > 0 ? (
                          profile.roles.map((r) => (
                            <CustomTag
                              key={r}
                              tone={roleTone(r)}
                              className={
                                r === 'ADMIN'
                                  ? 'border-transparent bg-primary/12 text-primary'
                                  : undefined
                              }
                            >
                              {r}
                            </CustomTag>
                          ))
                        ) : (
                          <span className="text-sm text-muted-foreground">
                            No roles assigned
                          </span>
                        )}
                      </div>
                    </div>

                    <div className="w-full shrink-0 sm:max-w-[220px]">
                      <CustomFileUpload
                        accept="image/*"
                        compact
                        buttonLabel={uploading ? 'Uploading…' : 'Upload photo'}
                        dropLabel="Drop image or click"
                        disabled={uploading}
                        onChange={(f) => void handleUpload(f)}
                      />
                    </div>
                  </div>

                  <div className="mt-6 grid grid-cols-1 gap-x-5 gap-y-4 sm:grid-cols-2">
                    <div>
                      <label className={fieldLabel} htmlFor="pf-name">
                        Display name
                      </label>
                      <CustomInput
                        id="pf-name"
                        value={form.displayName}
                        onChange={setField('displayName')}
                        placeholder="Your name"
                        className="h-10 rounded-lg shadow-sm"
                      />
                    </div>
                    <div>
                      <label className={fieldLabel} htmlFor="pf-email">
                        Email
                      </label>
                      <CustomInput
                        id="pf-email"
                        type="email"
                        value={form.email}
                        onChange={setField('email')}
                        placeholder="you@example.com"
                        className="h-10 rounded-lg shadow-sm"
                      />
                    </div>
                    <div>
                      <label className={fieldLabel} htmlFor="pf-phone">
                        Phone
                      </label>
                      <CustomInput
                        id="pf-phone"
                        value={form.phone}
                        onChange={setField('phone')}
                        placeholder="+91 …"
                        className="h-10 rounded-lg shadow-sm"
                      />
                    </div>
                    <div>
                      <label className={fieldLabel} htmlFor="pf-dob">
                        Date of birth
                      </label>
                      <CustomDatePicker
                        id="pf-dob"
                        value={form.dateOfBirth}
                        onChange={(v) =>
                          setForm((f) => ({ ...f, dateOfBirth: v }))
                        }
                        placeholder="dd/mm/yyyy"
                        className="h-10"
                        max={new Date().toISOString().slice(0, 10)}
                      />
                    </div>
                    <div>
                      <label className={fieldLabel} htmlFor="pf-designation">
                        Designation
                      </label>
                      <CustomInput
                        id="pf-designation"
                        value={form.designation}
                        onChange={setField('designation')}
                        placeholder="e.g. Sub-Inspector"
                        className="h-10 rounded-lg shadow-sm"
                      />
                    </div>
                    <div>
                      <label className={fieldLabel} htmlFor="pf-department">
                        Department
                      </label>
                      <CustomInput
                        id="pf-department"
                        value={form.department}
                        onChange={setField('department')}
                        placeholder="e.g. Cyber Crime"
                        className="h-10 rounded-lg shadow-sm"
                      />
                    </div>
                  </div>
                </>
              )}
            </div>

            {!loading && (
              <div className={cardFooterClass}>
                <CustomButton
                  variant="primary"
                  loading={saving}
                  disabled={!isDirty || saving}
                  onClick={() => void saveProfile()}
                  icon={<Save className="size-4" aria-hidden />}
                  className={cn(isDirty && !saving && primaryBtnClass)}
                >
                  Save changes
                </CustomButton>
                {isDirty ? (
                  <span className="text-xs font-medium text-primary">
                    Unsaved changes
                  </span>
                ) : (
                  <span className="text-xs text-muted-foreground">
                    All changes saved
                  </span>
                )}
              </div>
            )}
          </section>

          {/* Change password card */}
          <section className={cardClass}>
            <div className={cardHeaderClass}>
              <span className="flex size-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <KeyRound className="size-4" aria-hidden />
              </span>
              <div>
                <h2 className="m-0 text-sm font-semibold text-foreground">
                  Change password
                </h2>
                <p className="m-0 text-xs text-muted-foreground">
                  Use at least 8 characters for a new password
                </p>
              </div>
            </div>

            <div className={cardBodyClass}>
              <div className="grid grid-cols-1 gap-x-5 gap-y-4 sm:grid-cols-2">
                <div className="sm:col-span-2">
                  <label className={fieldLabel} htmlFor="pw-current">
                    Current password
                  </label>
                  <CustomInput
                    id="pw-current"
                    type="password"
                    autoComplete="current-password"
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                    placeholder="••••••••"
                    className="h-10 rounded-lg shadow-sm"
                  />
                </div>
                <div>
                  <label className={fieldLabel} htmlFor="pw-new">
                    New password
                  </label>
                  <CustomInput
                    id="pw-new"
                    type="password"
                    autoComplete="new-password"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    placeholder="At least 8 characters"
                    className="h-10 rounded-lg shadow-sm"
                  />
                </div>
                <div>
                  <label className={fieldLabel} htmlFor="pw-confirm">
                    Confirm new password
                  </label>
                  <CustomInput
                    id="pw-confirm"
                    type="password"
                    autoComplete="new-password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    placeholder="Re-enter new password"
                    className="h-10 rounded-lg shadow-sm"
                  />
                </div>
              </div>

              {pwError && (
                <div className="mt-4 rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2.5 text-sm text-destructive">
                  {pwError}
                </div>
              )}
            </div>

            <div className={cardFooterClass}>
              <CustomButton
                variant="primary"
                loading={changingPw}
                disabled={
                  changingPw ||
                  !currentPassword ||
                  !newPassword ||
                  !confirmPassword
                }
                onClick={() => void changePassword()}
                icon={<KeyRound className="size-4" aria-hidden />}
                className={cn(
                  currentPassword &&
                    newPassword &&
                    confirmPassword &&
                    !changingPw &&
                    primaryBtnClass,
                )}
              >
                Update password
              </CustomButton>
            </div>
          </section>
        </motion.div>
      </div>
    </div>
  );
};

export default ProfilePage;
