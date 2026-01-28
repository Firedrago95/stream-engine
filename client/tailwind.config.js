/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        chzzk_dark: '#1a1d23',
        chzzk_bg: '#21242c',
        chzzk_purple: '#8a2be2',
        chzzk_light_gray: '#a0a0a0',
        chzzk_text: '#e0e0e0',
      }
    },
  },
  plugins: [],
}
