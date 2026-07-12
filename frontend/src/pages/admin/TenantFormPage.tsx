import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useTenant, useCreateTenant, useUpdateTenant, useUploadLogo } from '../../api/tenants'
import { getErrorMessage } from '../../lib/api'

const MIN_PASSWORD_LENGTH = 8

export default function TenantFormPage() {
  const { id } = useParams<{ id?: string }>()
  const isEdit = Boolean(id)
  const navigate = useNavigate()

  const { data: existing } = useTenant(id ?? '')
  const create = useCreateTenant()
  const update = useUpdateTenant()
  const uploadLogo = useUploadLogo()

  const [name, setName] = useState('')
  const [slug, setSlug] = useState('')
  const [primaryColor, setPrimaryColor] = useState('#1e293b')
  const [active, setActive] = useState(true)
  const [logoFile, setLogoFile] = useState<File | null>(null)
  const [logoPreview, setLogoPreview] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)

  // Initial admin login — only collected when creating a new tenant.
  const [adminFullName, setAdminFullName] = useState('')
  const [adminUsername, setAdminUsername] = useState('')
  const [adminPassword, setAdminPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)

  // Post-create confirmation — shown instead of navigating away immediately,
  // since the password is never shown again after this.
  const [created, setCreated] = useState<{ name: string; slug: string; adminUsername: string } | null>(null)

  // Populate form when editing.
  useEffect(() => {
    if (existing) {
      setName(existing.name)
      setSlug(existing.slug)
      setPrimaryColor(existing.primaryColor ?? '#1e293b')
      setActive(existing.active)
      setLogoPreview(existing.logoUrl ?? null)
    }
  }, [existing])

  // Auto-generate slug from name when creating.
  const handleNameChange = (value: string) => {
    setName(value)
    if (!isEdit) {
      setSlug(value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, ''))
    }
  }

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0] ?? null
    setLogoFile(file)
    if (file) {
      const reader = new FileReader()
      reader.onload = (ev) => setLogoPreview(ev.target?.result as string)
      reader.readAsDataURL(file)
    }
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)

    // Client-side validation mirrors the backend rules, but the backend
    // re-validates independently — this is just for a fast, friendly error.
    if (!isEdit) {
      if (!adminFullName.trim() || !adminUsername.trim() || !adminPassword) {
        setError('Full name, username, and password are required for the initial admin login.')
        return
      }
      if (adminPassword.length < MIN_PASSWORD_LENGTH) {
        setError(`Admin password must be at least ${MIN_PASSWORD_LENGTH} characters.`)
        return
      }
    }

    setSubmitting(true)
    try {
      let savedId = id

      if (isEdit && id) {
        const input = { name, slug, primaryColor: primaryColor || undefined, active }
        await update.mutateAsync({ id, input })
      } else {
        const input = {
          name,
          slug,
          primaryColor: primaryColor || undefined,
          active,
          adminFullName: adminFullName.trim(),
          adminUsername: adminUsername.trim(),
          adminPassword,
        }
        const result = await create.mutateAsync(input)
        savedId = result.tenant.id

        // Upload logo separately if a file was chosen.
        if (logoFile && savedId) {
          await uploadLogo.mutateAsync({ id: savedId, file: logoFile })
        }

        // Show the confirmation screen instead of navigating away — the
        // password is never shown again after this point.
        setCreated({ name: result.tenant.name, slug: result.tenant.slug, adminUsername: result.adminUsername })
        setSubmitting(false)
        return
      }

      // Upload logo separately if a file was chosen (edit path).
      if (logoFile && savedId) {
        await uploadLogo.mutateAsync({ id: savedId, file: logoFile })
      }

      navigate('/admin/tenants')
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  // ── Post-create confirmation ─────────────────────────────────────────────
  if (created) {
    return (
      <div className="mx-auto max-w-lg space-y-6">
        <div className="rounded-xl border border-green-200 bg-green-50 p-8 text-center shadow">
          <div className="mb-2 text-4xl">✓</div>
          <h2 className="mb-1 text-xl font-bold text-green-800">Tenant Created</h2>
          <p className="mb-4 text-sm text-green-700">
            <span className="font-semibold">{created.name}</span>{' '}
            <span className="font-mono text-green-600">({created.slug})</span> is ready to use.
          </p>

          <div className="mx-auto mb-4 max-w-xs rounded-lg border border-slate-200 bg-white p-4 text-left">
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
              Admin Username
            </p>
            <p className="mb-3 font-mono text-sm text-slate-800">{created.adminUsername}</p>
            <p className="text-xs text-amber-700">
              Share these credentials with the client through a secure channel — the
              password you set won't be shown again.
            </p>
          </div>

          <button
            onClick={() => navigate('/admin/tenants')}
            className="rounded-md bg-green-700 px-6 py-2 text-white hover:bg-green-800"
          >
            Back to Tenants
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-xl space-y-6">
      <h1 className="text-2xl font-bold text-slate-800">
        {isEdit ? 'Edit Tenant' : 'New Tenant'}
      </h1>

      <form
        onSubmit={onSubmit}
        className="space-y-4 rounded-lg border border-slate-200 bg-white p-6 shadow-sm"
      >
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-slate-700">
            Name <span className="text-red-500">*</span>
          </span>
          <input
            value={name}
            onChange={(e) => handleNameChange(e.target.value)}
            required
            className="w-full rounded-md border border-slate-300 px-3 py-2"
          />
        </label>

        <label className="block">
          <span className="mb-1 block text-sm font-medium text-slate-700">
            Slug <span className="text-red-500">*</span>
            <span className="ml-1 text-xs font-normal text-slate-400">
              (lowercase letters, digits, hyphens)
            </span>
          </span>
          <input
            value={slug}
            onChange={(e) => setSlug(e.target.value)}
            required
            pattern="^[a-z0-9-]+$"
            className="w-full rounded-md border border-slate-300 px-3 py-2 font-mono text-sm"
          />
        </label>

        <div className="flex items-end gap-4">
          <label className="block flex-1">
            <span className="mb-1 block text-sm font-medium text-slate-700">
              Primary color
            </span>
            <div className="flex gap-2">
              <input
                type="color"
                value={primaryColor}
                onChange={(e) => setPrimaryColor(e.target.value)}
                className="h-10 w-12 cursor-pointer rounded border border-slate-300 p-0.5"
              />
              <input
                value={primaryColor}
                onChange={(e) => setPrimaryColor(e.target.value)}
                placeholder="#1e293b"
                className="flex-1 rounded-md border border-slate-300 px-3 py-2 font-mono text-sm"
              />
            </div>
          </label>

          <label className="flex cursor-pointer items-center gap-2 pb-2">
            <input
              type="checkbox"
              checked={active}
              onChange={(e) => setActive(e.target.checked)}
              className="h-4 w-4 rounded border-slate-300"
            />
            <span className="text-sm font-medium text-slate-700">Active</span>
          </label>
        </div>

        {/* Logo upload */}
        <div>
          <span className="mb-1 block text-sm font-medium text-slate-700">Logo</span>
          <div className="flex items-center gap-4">
            {logoPreview && (
              <img
                src={logoPreview}
                alt="Logo preview"
                className="h-12 w-12 rounded border border-slate-200 object-contain"
              />
            )}
            <button
              type="button"
              onClick={() => fileRef.current?.click()}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-600 hover:bg-slate-50"
            >
              {logoPreview ? 'Change logo' : 'Upload logo'}
            </button>
            {logoFile && (
              <span className="text-xs text-slate-500">{logoFile.name}</span>
            )}
          </div>
          <input
            ref={fileRef}
            type="file"
            accept="image/*"
            onChange={handleFileChange}
            className="hidden"
          />
          {isEdit && (
            <p className="mt-1 text-xs text-slate-400">
              Logo is uploaded to S3 — requires S3 storage to be configured.
            </p>
          )}
        </div>

        {/* Initial admin login — only collected when creating a new tenant. */}
        {!isEdit && (
          <div className="space-y-4 rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-600">
              Initial Admin Login
            </h2>
            <p className="text-xs text-slate-500">
              This creates the first login for this tenant. The client can change
              the password after they sign in.
            </p>

            <label className="block">
              <span className="mb-1 block text-sm font-medium text-slate-700">
                Full Name <span className="text-red-500">*</span>
              </span>
              <input
                id="tenant-admin-full-name"
                name="adminFullName"
                value={adminFullName}
                onChange={(e) => setAdminFullName(e.target.value)}
                required
                className="w-full rounded-md border border-slate-300 px-3 py-2"
              />
            </label>

            <label className="block">
              <span className="mb-1 block text-sm font-medium text-slate-700">
                Username <span className="text-red-500">*</span>
              </span>
              <input
                id="tenant-admin-username"
                name="adminUsername"
                value={adminUsername}
                onChange={(e) => setAdminUsername(e.target.value)}
                required
                className="w-full rounded-md border border-slate-300 px-3 py-2"
              />
            </label>

            <label className="block">
              <span className="mb-1 block text-sm font-medium text-slate-700">
                Password <span className="text-red-500">*</span>
                <span className="ml-1 text-xs font-normal text-slate-400">
                  (min {MIN_PASSWORD_LENGTH} characters)
                </span>
              </span>
              <div className="flex gap-2">
                <input
                  id="tenant-admin-password"
                  name="adminPassword"
                  type={showPassword ? 'text' : 'password'}
                  value={adminPassword}
                  onChange={(e) => setAdminPassword(e.target.value)}
                  required
                  minLength={MIN_PASSWORD_LENGTH}
                  autoComplete="new-password"
                  className="flex-1 rounded-md border border-slate-300 px-3 py-2"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  className="rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-600 hover:bg-slate-100"
                >
                  {showPassword ? 'Hide' : 'Show'}
                </button>
              </div>
            </label>
          </div>
        )}

        {error && <p className="text-sm text-red-600">{error}</p>}

        <div className="flex gap-3 pt-2">
          <button
            type="submit"
            disabled={submitting}
            className="rounded-md bg-brand px-4 py-2 text-sm font-medium text-white hover:opacity-90 disabled:opacity-60"
          >
            {submitting ? 'Saving…' : isEdit ? 'Save changes' : 'Create tenant'}
          </button>
          <button
            type="button"
            onClick={() => navigate('/admin/tenants')}
            className="rounded-md border border-slate-300 px-4 py-2 text-sm text-slate-600"
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  )
}
