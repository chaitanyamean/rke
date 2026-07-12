import { useState, type FormEvent } from 'react'
import {
  useStaffUsers,
  useCreateStaffUser,
  useUpdateStaffUser,
  type StaffUser,
} from '../../api/staffUsers'
import { getErrorMessage } from '../../lib/api'

const MIN_PASSWORD_LENGTH = 8

export default function StaffUsersPage() {
  const { data: staffUsers = [], isLoading } = useStaffUsers()
  const create = useCreateStaffUser()
  const update = useUpdateStaffUser()

  const [fullName, setFullName] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [editingId, setEditingId] = useState<string | null>(null)
  const [editFullName, setEditFullName] = useState('')
  const [editActive, setEditActive] = useState(true)
  const [editPassword, setEditPassword] = useState('')
  const [showEditPassword, setShowEditPassword] = useState(false)
  const [editError, setEditError] = useState<string | null>(null)

  const resetCreateForm = () => {
    setFullName('')
    setUsername('')
    setPassword('')
    setShowPassword(false)
    setError(null)
  }

  const onCreate = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!fullName.trim() || !username.trim() || !password) {
      setError('Full name, username, and password are all required.')
      return
    }
    if (password.length < MIN_PASSWORD_LENGTH) {
      setError(`Password must be at least ${MIN_PASSWORD_LENGTH} characters.`)
      return
    }
    try {
      await create.mutateAsync({
        fullName: fullName.trim(),
        username: username.trim(),
        password,
      })
      resetCreateForm()
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  const startEdit = (user: StaffUser) => {
    setEditingId(user.id)
    setEditFullName(user.fullName ?? '')
    setEditActive(user.active)
    setEditPassword('')
    setShowEditPassword(false)
    setEditError(null)
  }

  const cancelEdit = () => {
    setEditingId(null)
    setEditError(null)
  }

  const saveEdit = async (user: StaffUser) => {
    setEditError(null)
    if (!editFullName.trim()) {
      setEditError('Full name is required.')
      return
    }
    if (editPassword && editPassword.length < MIN_PASSWORD_LENGTH) {
      setEditError(`Password must be at least ${MIN_PASSWORD_LENGTH} characters.`)
      return
    }
    try {
      await update.mutateAsync({
        id: user.id,
        input: {
          fullName: editFullName.trim(),
          active: editActive,
          newPassword: editPassword || undefined,
        },
      })
      setEditingId(null)
    } catch (err) {
      setEditError(getErrorMessage(err))
    }
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-slate-800">Staff Users</h1>
      <p className="text-sm text-slate-500">
        Create and manage staff logins for your tenant. Staff accounts can create
        farmers, sales, payments, and returns, but cannot edit existing
        transactions or manage other users.
      </p>

      <form
        onSubmit={onCreate}
        className="space-y-4 rounded-lg border border-slate-200 bg-white p-5 shadow-sm"
      >
        <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-600">
          Create Staff User
        </h2>

        <div className="grid gap-4 sm:grid-cols-3">
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">
              Full Name <span className="text-red-500">*</span>
            </span>
            <input
              id="staff-create-full-name"
              name="fullName"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>

          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">
              Username <span className="text-red-500">*</span>
            </span>
            <input
              id="staff-create-username"
              name="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
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
                id="staff-create-password"
                name="password"
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
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

        {error && <p className="text-sm text-red-600">{error}</p>}

        <button
          type="submit"
          disabled={create.isPending}
          className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-60"
        >
          {create.isPending ? 'Creating…' : 'Create staff user'}
        </button>
      </form>

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="px-4 py-2 font-medium">Full Name</th>
              <th className="px-4 py-2 font-medium">Username</th>
              {/* <th className="px-4 py-2 font-medium">Status</th> */}
              <th className="px-4 py-2 font-medium">New Password</th>
              <th className="px-4 py-2" />
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td colSpan={5} className="px-4 py-6 text-center text-slate-400">Loading…</td>
              </tr>
            )}
            {!isLoading && staffUsers.length === 0 && (
              <tr>
                <td colSpan={5} className="px-4 py-6 text-center text-slate-400">No staff users yet</td>
              </tr>
            )}
            {staffUsers.map((user) => {
              const editing = editingId === user.id
              return (
                <tr key={user.id} className="border-t border-slate-100 align-top">
                  <td className="px-4 py-2 text-slate-800">
                    {editing ? (
                      <input
                        value={editFullName}
                        onChange={(e) => setEditFullName(e.target.value)}
                        className="w-full rounded-md border border-slate-300 px-2 py-1"
                      />
                    ) : (
                      user.fullName || '—'
                    )}
                  </td>
                  <td className="px-4 py-2 font-mono text-xs text-slate-600">{user.username}</td>
                  {/* <td className="px-4 py-2">
                    {editing ? (
                      <label className="flex items-center gap-2 text-sm text-slate-700">
                        <input
                          type="checkbox"
                          checked={editActive}
                          onChange={(e) => setEditActive(e.target.checked)}
                          className="h-4 w-4 rounded border-slate-300"
                        />
                        Active
                      </label>
                    ) : (
                      <span
                        className={`rounded px-2 py-0.5 text-xs font-medium ${
                          user.active ? 'bg-green-100 text-green-700' : 'bg-slate-200 text-slate-600'
                        }`}
                      >
                        {user.active ? 'Active' : 'Inactive'}
                      </span>
                    )}
                  </td> */}
                  <td className="px-4 py-2">
                    {editing ? (
                      <div className="flex gap-2">
                        <input
                          type={showEditPassword ? 'text' : 'password'}
                          value={editPassword}
                          onChange={(e) => setEditPassword(e.target.value)}
                          placeholder="Leave blank to keep current"
                          autoComplete="new-password"
                          className="w-40 rounded-md border border-slate-300 px-2 py-1 text-sm"
                        />
                        <button
                          type="button"
                          onClick={() => setShowEditPassword((v) => !v)}
                          className="rounded-md border border-slate-300 px-2 py-1 text-xs text-slate-600 hover:bg-slate-100"
                        >
                          {showEditPassword ? 'Hide' : 'Show'}
                        </button>
                      </div>
                    ) : (
                      <span className="text-xs text-slate-400">—</span>
                    )}
                  </td>
                  <td className="px-4 py-2 text-right">
                    {editing ? (
                      <div className="flex flex-col items-end gap-1">
                        <div className="flex gap-2">
                          <button
                            onClick={() => saveEdit(user)}
                            disabled={update.isPending}
                            className="text-sm text-green-700 hover:underline"
                          >
                            Save
                          </button>
                          <button onClick={cancelEdit} className="text-sm text-slate-500 hover:underline">
                            Cancel
                          </button>
                        </div>
                        {editError && <p className="text-xs text-red-600">{editError}</p>}
                      </div>
                    ) : (
                      <button onClick={() => startEdit(user)} className="text-sm text-slate-600 hover:underline">
                        Edit
                      </button>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}
