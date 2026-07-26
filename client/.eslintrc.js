module.exports = {
  root: true,
  parser: '@typescript-eslint/parser',
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: 'module',
    ecmaFeatures: { jsx: true },
  },
  env: { browser: true, es2022: true, node: true },
  ignorePatterns: ['build/', 'legacy/', 'node_modules/'],
  rules: {
    'no-restricted-imports': [
      'error',
      {
        paths: [
          {
            name: 'antd',
            message:
              'Import from @atoms/* instead. Raw AntD usage is only allowed inside src/components/atoms/.',
          },
          {
            name: '@ant-design/icons',
            message:
              'Wrap AntD icons in an atom or import only inside src/components/atoms/.',
          },
        ],
      },
    ],
  },
  overrides: [
    {
      // AntD is only allowed inside the custom-atom catalog plus the two
      // infrastructure files that bridge AntD into the app: the ConfigProvider
      // and the notification context. Everything else must consume atoms.
      files: [
        'src/components/atoms/**/*.{ts,tsx}',
        'src/components/templates/**/*.{ts,tsx}',
        'src/providers/**/*.{ts,tsx}',
        'src/utils/openNotification.ts',
        'src/App.tsx',
      ],
      rules: { 'no-restricted-imports': 'off' },
    },
  ],
};
