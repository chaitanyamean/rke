import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useTenant, useCreateTenant, useUpdateTenant, useUploadLogo } from '../../api/tenants'
import { getErrorMessage } from '../../lib/api'

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
    setSubmitting(true)

    try {
      const input = { name, slug, primaryColor: primaryColor || undefined, active }

      let savedId = id
      if (isEdit && id) {
        await update.mutateAsync({ id, input })
      } else {
        const created = await create.mutateAsync(input)
        savedId = created.id
      }

      // Upload logo separately if a file was chosen.
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
