/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#effaf3',
          100: '#d8f3e1',
          200: '#b3e5c6',
          300: '#7fd0a3',
          400: '#47b37b',
          500: '#23965f',
          600: '#15784b',
          700: '#12603e',
          800: '#114d34',
          900: '#0e3f2c',
          950: '#062418',
        },
        ink: {
          50: '#f7f8f7',
          100: '#eef0ee',
          200: '#dde1de',
          300: '#c3c9c5',
          400: '#8f9893',
          500: '#66706b',
          600: '#4b5550',
          700: '#3a423e',
          800: '#252b28',
          900: '#161a18',
        },
        saffron: { 500: '#e8891c', 600: '#c96f0c' },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'sans-serif'],
      },
      boxShadow: {
        card: '0 1px 2px rgba(16, 24, 20, 0.04), 0 1px 3px rgba(16, 24, 20, 0.06)',
        raised: '0 4px 12px rgba(16, 24, 20, 0.08)',
      },
    },
  },
  plugins: [],
};
