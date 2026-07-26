import React, { useEffect, useMemo, useState } from 'react';
import { Check, Copy } from 'lucide-react';
import CustomDrawer from '@atoms/CustomDrawer';
import CustomInput from '@atoms/CustomInput';
import CustomDatePicker from '@atoms/CustomDatePicker';
import CustomButton from '@atoms/CustomButton';
import CustomSwitch from '@atoms/CustomSwitch';
import CustomFileUpload from '@atoms/CustomFileUpload';
import { usersApi } from '@apiCalls/services';
import { useNotification } from '@providers/NotificationProviders';
import { APP_ROLES } from '@interfaces/user.interface';
import type {
  AppRole,
  UserDto,
  CreateUserRequest,
  UpdateUserRequest,
} from '@interfaces/user.interface';

export interface UserFormDrawerProps {
  open: boolean;
  mode: 'create' | 'edit';
  user?: UserDto;
  onClose: () => void;
  onSaved: () => void;
}

interface FormState {
  username: string;
  displayName: string;
  email: string;
  dateOfBirth: string;
  phone: string;
  designation: string;
  department: string;
  roles: AppRole[];
  enabled: boolean;
}

const EMPTY_FORM: FormState = {
  username: '',
  displayName: '',
  email: '',
  dateOfBirth: '',
  phone: '',
  designation: '',
  department: '',
  roles: ['ANALYST'],
  enabled: true,
};

const ROLE_LABEL: Record<AppRole, string> = {
  ADMIN: 'Admin',
  SUPERVISOR: 'Supervisor',
  INVESTIGATOR: 'Investigator',
  ANALYST: 'Analyst',
  POLICYMAKER: 'Policymaker',
};

const emailValid = (value: string): boolean =>
  /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);

const serverError = (err: unknown): string | null => {
  const data = (err as { response?: { data?: { error?: string } } })?.response
    ?.data;
  return data?.error || null;
};

const fromUser = (user: UserDto): FormState => ({
  username: user.username,
  displayName: user.displayName || '',
  email: user.email || '',
  dateOfBirth: user.dateOfBirth || '',
  phone: user.phone || '',
  designation: user.designation || '',
  department: user.department || '',
  roles: (user.roles.filter((r) =>
    (APP_ROLES as string[]).includes(r),
  ) as AppRole[]) || ['ANALYST'],
  enabled: user.enabled,
});

const labelClass =
  'mb-1 block text-[11px] font-semibold uppercase tracking-[0.05em] text-foreground';
const fieldErrorClass = 'mt-1 text-xs text-destructive';

const UserFormDrawer: React.FC<UserFormDrawerProps> = ({
  open,
  mode,
  user,
  onClose,
  onSaved,
}) => {
  const notify = useNotification();
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [photo, setPhoto] = useState<File | null>(null);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [tempPassword, setTempPassword] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  // Reset local state whenever the drawer opens (or its target user changes).
  useEffect(() => {
    if (!open) return;
    setForm(mode === 'edit' && user ? fromUser(user) : EMPTY_FORM);
    setPhoto(null);
    setErrors({});
    setTempPassword(null);
    setCopied(false);
    setSaving(false);
  }, [open, mode, user]);

  const patch = (next: Partial<FormState>) =>
    setForm((f) => ({ ...f, ...next }));

  const toggleRole = (role: AppRole) => {
    setForm((f) => {
      const has = f.roles.includes(role);
      const roles = has
        ? f.roles.filter((r) => r !== role)
        : [...f.roles, role];
      return { ...f, roles };
    });
  };

  const usernameHint = useMemo(() => {
    const uname = form.username.trim().toLowerCase();
    const year = form.dateOfBirth ? form.dateOfBirth.slice(0, 4) : '';
    if (mode !== 'create' || !uname || !year) return null;
    return `${uname.replace(/[^a-z0-9]/g, '').slice(0, 4)}${year}`;
  }, [form.username, form.dateOfBirth, mode]);

  const validate = (): boolean => {
    const next: Record<string, string> = {};
    if (mode === 'create') {
      const uname = form.username.trim();
      if (!uname) next.username = 'Username is required.';
      else if (!/^[a-z0-9._-]+$/.test(uname))
        next.username = 'Lowercase letters, numbers, and . _ - only (no spaces).';
      if (!form.dateOfBirth) next.dateOfBirth = 'Date of birth is required.';
    }
    if (form.email.trim() && !emailValid(form.email.trim()))
      next.email = 'Enter a valid email address.';
    if (form.roles.length === 0) next.roles = 'Select at least one role.';
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const submit = async () => {
    if (!validate()) return;
    setSaving(true);
    try {
      if (mode === 'create') {
        const body: CreateUserRequest = {
          username: form.username.trim().toLowerCase(),
          roles: form.roles,
          dateOfBirth: form.dateOfBirth,
          enabled: form.enabled,
        };
        if (form.displayName.trim()) body.displayName = form.displayName.trim();
        if (form.email.trim()) body.email = form.email.trim();
        if (form.phone.trim()) body.phone = form.phone.trim();
        if (form.designation.trim()) body.designation = form.designation.trim();
        if (form.department.trim()) body.department = form.department.trim();

        const { user: created, temporaryPassword } =
          await usersApi.create(body);
        if (photo) {
          try {
            await usersApi.uploadPhoto(created.id, photo);
          } catch {
            notify('User created, but the photo failed to upload.', 'Warning');
          }
        }
        setTempPassword(temporaryPassword);
        notify(`User "${created.username}" created`, 'Success');
        onSaved();
      } else if (user) {
        const body: UpdateUserRequest = {};
        const orig = fromUser(user);
        if (form.displayName.trim() !== orig.displayName)
          body.displayName = form.displayName.trim();
        if (form.email.trim() !== orig.email) body.email = form.email.trim();
        if (form.dateOfBirth !== orig.dateOfBirth)
          body.dateOfBirth = form.dateOfBirth;
        if (form.phone.trim() !== orig.phone) body.phone = form.phone.trim();
        if (form.designation.trim() !== orig.designation)
          body.designation = form.designation.trim();
        if (form.department.trim() !== orig.department)
          body.department = form.department.trim();
        if (
          form.roles.slice().sort().join(',') !==
          orig.roles.slice().sort().join(',')
        )
          body.roles = form.roles;
        if (form.enabled !== orig.enabled) body.enabled = form.enabled;

        if (Object.keys(body).length > 0) {
          await usersApi.update(user.id, body);
        }
        if (photo) {
          await usersApi.uploadPhoto(user.id, photo);
        }
        notify(`User "${form.username}" updated`, 'Success');
        onSaved();
        onClose();
      }
    } catch (err) {
      notify(
        serverError(err) || (err as Error)?.message || 'Failed to save user',
        'Error',
      );
    } finally {
      setSaving(false);
    }
  };

  const copyPassword = async () => {
    if (!tempPassword) return;
    try {
      await navigator.clipboard.writeText(tempPassword);
      setCopied(true);
      setTimeout(() => setCopied(false), 1800);
    } catch {
      notify('Could not copy to clipboard.', 'Warning');
    }
  };

  const title = mode === 'create' ? 'New user' : `Edit ${form.username}`;

  return (
    <CustomDrawer
      open={open}
      title={title}
      onClose={onClose}
      placement="right"
      width={460}
      footer={
        tempPassword ? (
          <CustomButton variant="primary" onClick={onClose}>
            Done
          </CustomButton>
        ) : (
          <>
            <CustomButton
              variant="secondary"
              onClick={onClose}
              disabled={saving}
            >
              Cancel
            </CustomButton>
            <CustomButton
              variant="primary"
              onClick={() => void submit()}
              loading={saving}
            >
              {mode === 'create' ? 'Create user' : 'Save changes'}
            </CustomButton>
          </>
        )
      }
    >
      <div className="flex flex-col gap-4 px-5 py-5">
        {tempPassword ? (
          <div className="flex flex-col gap-3 rounded-lg border border-green-200 bg-green-50 p-4 dark:border-green-500/30 dark:bg-green-500/10">
            <div className="text-sm font-semibold text-green-800 dark:text-green-300">
              User created
            </div>
            <div>
              <div className={labelClass}>Temporary password</div>
              <div className="flex items-center gap-2">
                <code className="flex-1 rounded-md border border-border bg-card px-3 py-2 font-mono text-sm text-foreground">
                  {tempPassword}
                </code>
                <CustomButton
                  variant="ghost"
                  size="small"
                  onClick={() => void copyPassword()}
                  icon={
                    copied ? (
                      <Check className="size-4" />
                    ) : (
                      <Copy className="size-4" />
                    )
                  }
                >
                  {copied ? 'Copied' : 'Copy'}
                </CustomButton>
              </div>
            </div>
            <p className="m-0 text-xs leading-relaxed text-muted-foreground">
              Share this with the user. They must change it on first login.
            </p>
          </div>
        ) : (
          <>
            {mode === 'create' && (
              <div>
                <label className={labelClass}>Username</label>
                <CustomInput
                  value={form.username}
                  onChange={(e) =>
                    patch({
                      username: e.target.value.toLowerCase().replace(/\s/g, ''),
                    })
                  }
                  placeholder="e.g. rkumar"
                  autoFocus
                  fullWidth
                />
                {errors.username ? (
                  <div className={fieldErrorClass}>{errors.username}</div>
                ) : (
                  <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
                    First-time password = first 4 letters of username + birth
                    year (e.g. rkum1990).
                    {usernameHint && (
                      <>
                        {' '}
                        This user&apos;s will be{' '}
                        <span className="font-mono font-medium text-foreground">
                          {usernameHint}
                        </span>
                        .
                      </>
                    )}
                  </p>
                )}
              </div>
            )}

            <div>
              <label className={labelClass}>Display name</label>
              <CustomInput
                value={form.displayName}
                onChange={(e) => patch({ displayName: e.target.value })}
                placeholder="e.g. Riya Kumar"
                fullWidth
              />
            </div>

            <div>
              <label className={labelClass}>Email</label>
              <CustomInput
                type="email"
                value={form.email}
                onChange={(e) => patch({ email: e.target.value })}
                placeholder="name@agency.gov"
                fullWidth
              />
              {errors.email && (
                <div className={fieldErrorClass}>{errors.email}</div>
              )}
            </div>

            <div>
              <label className={labelClass}>
                Date of birth{mode === 'create' ? '' : ' (optional)'}
              </label>
              <CustomDatePicker
                value={form.dateOfBirth}
                onChange={(v) => patch({ dateOfBirth: v })}
                placeholder="dd/mm/yyyy"
                max={new Date().toISOString().slice(0, 10)}
              />
              {errors.dateOfBirth && (
                <div className={fieldErrorClass}>{errors.dateOfBirth}</div>
              )}
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className={labelClass}>Phone</label>
                <CustomInput
                  value={form.phone}
                  onChange={(e) => patch({ phone: e.target.value })}
                  placeholder="+91 80 2294 2222"
                  fullWidth
                />
              </div>
              <div>
                <label className={labelClass}>Designation</label>
                <CustomInput
                  value={form.designation}
                  onChange={(e) => patch({ designation: e.target.value })}
                  placeholder="e.g. Detective"
                  fullWidth
                />
              </div>
            </div>

            <div>
              <label className={labelClass}>Department</label>
              <CustomInput
                value={form.department}
                onChange={(e) => patch({ department: e.target.value })}
                placeholder="e.g. Cyber Crime Unit"
                fullWidth
              />
            </div>

            <div>
              <label className={labelClass}>Roles</label>
              <div className="flex flex-col gap-1.5">
                {APP_ROLES.map((role) => {
                  const checked = form.roles.includes(role);
                  return (
                    <label
                      key={role}
                      className="flex cursor-pointer items-center gap-2.5 rounded-md border border-border px-3 py-2 text-sm transition-colors hover:bg-accent hover:text-accent-foreground"
                    >
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={() => toggleRole(role)}
                        className="size-4 accent-primary"
                      />
                      <span className="font-medium text-foreground">
                        {ROLE_LABEL[role]}
                      </span>
                    </label>
                  );
                })}
              </div>
              {errors.roles && (
                <div className={fieldErrorClass}>{errors.roles}</div>
              )}
            </div>

            {mode === 'edit' && (
              <div className="flex items-center justify-between rounded-md border border-border px-3 py-2.5">
                <div>
                  <div className="text-sm font-medium text-foreground">
                    Account active
                  </div>
                  <div className="text-xs text-muted-foreground">
                    Disabled users cannot sign in.
                  </div>
                </div>
                <CustomSwitch
                  checked={form.enabled}
                  onChange={(v) => patch({ enabled: v })}
                  ariaLabel="Account active"
                />
              </div>
            )}

            <div>
              <label className={labelClass}>Profile photo</label>
              <CustomFileUpload
                value={photo}
                onChange={setPhoto}
                accept="image/*"
                dropLabel="Click or drop an image"
                buttonLabel="Choose image"
              />
            </div>
          </>
        )}
      </div>
    </CustomDrawer>
  );
};

export default UserFormDrawer;
