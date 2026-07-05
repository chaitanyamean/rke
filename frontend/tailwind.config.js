/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        // CSS variable set at runtime from tenant.primaryColor (see AuthContext).
        // Usage: bg-brand, text-brand, border-brand etc.
        brand: 'var(--color-brand, #1e293b)',
      },
    },
  },
  plugins: [],
}
