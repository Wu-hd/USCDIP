/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,ts}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['DM Sans', 'Inter', 'system-ui', 'sans-serif'],
        display: ['Space Grotesk', 'DM Sans', 'system-ui', 'sans-serif']
      },
      colors: {
        ink: '#0B0B10',
        panel: '#121218',
        line: 'rgba(255,255,255,0.12)',
        primary: '#2563EB',
        primarySoft: '#3B82F6',
        accent: '#F97316',
        amber: '#F59E0B'
      },
      boxShadow: {
        glow: '0 0 40px rgba(37, 99, 235, 0.18)',
        amber: '0 0 32px rgba(249, 115, 22, 0.16)'
      }
    }
  },
  plugins: []
};
