// tailwind.config.js
/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{js,jsx,ts,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#eff6ff',
          500: '#3b82f6',
          600: '#2563eb',
          700: '#1d4ed8',
        },
        severity: {
          critical: '#dc2626',
          major: '#f97316',
          minor: '#eab308',
          info: '#3b82f6',
        }
      },
    },
  },
  plugins: [],
}