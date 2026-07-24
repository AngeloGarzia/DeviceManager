/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  theme: {
    extend: {
      colors: {
        surface: {
          DEFAULT: '#f4f6f8',
          card: '#ffffff',
          muted: '#e8edf2'
        },
        ink: {
          DEFAULT: '#1f2933',
          soft: '#52606d',
          faint: '#7b8794'
        },
        brand: {
          DEFAULT: '#2f5d7a',
          soft: '#3d7394',
          faint: '#e8f0f5'
        },
        line: '#d9e2ec'
      },
      fontFamily: {
        sans: ['"IBM Plex Sans"', 'Segoe UI', 'sans-serif']
      },
      boxShadow: {
        soft: '0 1px 2px rgba(16, 24, 40, 0.04), 0 8px 24px rgba(16, 24, 40, 0.06)'
      }
    }
  },
  plugins: [],
  corePlugins: {
    preflight: false
  }
};
