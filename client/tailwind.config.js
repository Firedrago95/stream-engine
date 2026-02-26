/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        chzzk: {
          green: '#00FFA3',
          dark: '#0C0D0E',
          card: '#1B1C1E',
          hover: '#252629'
        }
      },
      boxShadow: {
        'neon': '0 0 15px rgba(0, 255, 163, 0.3)',
        'analyzing': '0 0 20px rgba(147, 51, 234, 0.5)',
      }
    },
  },
  plugins: [],
}
