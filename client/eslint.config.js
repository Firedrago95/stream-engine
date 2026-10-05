import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
    rules: {
      'no-restricted-syntax': [
        'error',
        {
          selector: 'JSXAttribute[name.name="className"] Literal[value=/\\btext-(gray-[3-9]00|slate-|zinc-|neutral-)/]',
          message: '다크 테마 텍스트 색상은 text-white, text-gray-100, text-gray-200만 허용됩니다.',
        },
        {
          selector: 'JSXAttribute[name.name="className"] TemplateElement[value.raw=/\\btext-(gray-[3-9]00|slate-|zinc-|neutral-)/]',
          message: '다크 테마 텍스트 색상은 text-white, text-gray-100, text-gray-200만 허용됩니다.',
        },
      ],
    },
  },
])
