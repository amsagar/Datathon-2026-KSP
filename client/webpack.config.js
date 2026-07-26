const CopyWebpackPlugin = require('copy-webpack-plugin');
const path = require('path');
const Dotenv = require('dotenv-webpack');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const webpack = require('webpack');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const ReactRefreshWebpackPlugin = require('@pmmmwh/react-refresh-webpack-plugin');

require('dotenv').config({ path: './.env.development' });
const API_URL = process.env.BASE_URL || 'http://localhost:8080';

// camelCase -> kebab-case (with `--` after the first camel boundary), used by
// the BEM `getLocalIdent` so SCSS class names match mvt-v2's convention.
const camelToKebab = (str) => {
  const result = str.replace(/([a-z])([A-Z])/g, '$1-$2').toLowerCase();
  if (/[A-Z]/.test(str)) {
    return result.replace(/-/, '--');
  }
  return result;
};

module.exports = (env, argv) => {
  const mode = argv.mode || 'development';
  const isDev = mode === 'development';

  return {
    // hidden-source-map in prod: maps are emitted for error tooling but not
    // referenced from the bundles, so browsers never fetch them.
    devtool: isDev ? 'cheap-module-source-map' : 'hidden-source-map',
    mode,
    entry: {
      main: './src/main.tsx',
    },
    target: 'web',
    module: {
      rules: [
        {
          test: /\.(js|jsx|ts|tsx)$/,
          exclude: /node_modules/,
          use: {
            loader: 'babel-loader',
            options: {
              presets: [
                ['@babel/preset-env', { targets: { esmodules: true } }],
                ['@babel/preset-react', { runtime: 'automatic' }],
                '@babel/preset-typescript',
              ],
              plugins: [isDev && require.resolve('react-refresh/babel')].filter(
                Boolean
              ),
            },
          },
        },
        {
          test: /\.module\.s[ac]ss$/i,
          use: [
            MiniCssExtractPlugin.loader,
            {
              loader: 'css-loader',
              options: {
                modules: {
                  getLocalIdent: (context, _localIdentName, localName) => {
                    const componentName = path.basename(
                      context.resourcePath,
                      '.scss'
                    );
                    const transformedLocalName =
                      camelToKebab(localName) || 'default';
                    const hash = Buffer.from(
                      `${context.resourcePath}${localName}`
                    )
                      .toString('base64')
                      .substring(0, 6);
                    const [element, modifier] = transformedLocalName.split('-');
                    if (modifier) {
                      return `${camelToKebab(componentName)}__${element}--${modifier}--${hash}`;
                    }
                    return `${camelToKebab(componentName)}__${transformedLocalName}--${hash}`;
                  },
                },
                sourceMap: true,
              },
            },
            'postcss-loader',
            'sass-loader',
          ],
        },
        {
          // Non-module SCSS (global.scss, partials).
          test: /\.s[ac]ss$/i,
          exclude: /\.module\.(s[ac]ss|css)$/i,
          use: [
            MiniCssExtractPlugin.loader,
            'css-loader',
            'postcss-loader',
            'sass-loader',
          ],
        },
        {
          // Plain CSS (Tailwind entry `tailwind.css`, third-party CSS). No
          // sass-loader — it would choke on `@import "tailwindcss"`. PostCSS
          // (Tailwind v4 plugin) expands the Tailwind imports here.
          test: /\.css$/i,
          use: [MiniCssExtractPlugin.loader, 'css-loader', 'postcss-loader'],
        },
        {
          test: /\.svg$/,
          use: ['@svgr/webpack'],
        },
        {
          test: /\.(png|jpg|jpeg|gif|bmp|webp)$/,
          type: 'asset',
          parser: {
            dataUrlCondition: { maxSize: 8192 },
          },
          generator: {
            filename: 'assets/[name].[hash:8][ext]',
          },
        },
      ],
    },
    plugins: [
      new Dotenv({
        path: path.resolve(__dirname, '.env.development'),
        systemvars: true,
      }),
      isDev && new webpack.HotModuleReplacementPlugin(),
      isDev && new ReactRefreshWebpackPlugin(),
      new HtmlWebpackPlugin({
        template: './public/index.html',
        filename: './index.html',
        inject: true,
      }),
      new MiniCssExtractPlugin({ filename: '[name].css' }),
      new CopyWebpackPlugin({
        patterns: [
          {
            from: 'public',
            to: '.',
            globOptions: { ignore: ['**/index.html'] },
          },
        ],
      }),
      new webpack.DefinePlugin({
        'process.env.NODE_ENV': JSON.stringify(mode),
        // Local: leave empty for webpack /api proxy. Prod container sets streamApiBase at runtime.
        'process.env.STREAM_API_BASE': JSON.stringify(
          process.env.STREAM_API_BASE || ''
        ),
      }),
    ].filter(Boolean),
    resolve: {
      extensions: ['.js', '.jsx', '.ts', '.tsx'],
      alias: {
        '@': path.resolve(__dirname, './src'),
        '@src': path.resolve(__dirname, './src'),
        '@atoms': path.resolve(__dirname, './src/components/atoms'),
        '@molecules': path.resolve(__dirname, './src/components/molecules'),
        '@organisms': path.resolve(__dirname, './src/components/organisms'),
        '@templates': path.resolve(__dirname, './src/components/templates'),
        '@pages': path.resolve(__dirname, './src/components/pages'),
        '@routes': path.resolve(__dirname, './src/components/routes'),
        '@providers': path.resolve(__dirname, './src/providers'),
        '@store': path.resolve(__dirname, './src/store'),
        '@interfaces': path.resolve(__dirname, './src/interfaces'),
        '@utils': path.resolve(__dirname, './src/utils'),
        '@constants': path.resolve(__dirname, './src/constants'),
        '@apiCalls': path.resolve(__dirname, './src/apiCalls'),
        '@config': path.resolve(__dirname, './src/config'),
        '@styles': path.resolve(__dirname, './src/styles'),
        '@assets': path.resolve(__dirname, './src/assets'),
      },
      modules: [path.resolve(__dirname, 'src'), 'node_modules'],
    },
    output: {
      filename: '[name].[contenthash].js',
      path: path.resolve(__dirname, 'build'),
      publicPath: '/',
      clean: true,
    },
    optimization: {
      runtimeChunk: 'single',
      splitChunks: {
        chunks: 'all',
        cacheGroups: {
          // Framework: changes rarely, caches long.
          react: {
            test: /[\\/]node_modules[\\/](react|react-dom|react-router|react-router-dom|scheduler)[\\/]/,
            name: 'react',
            chunks: 'all',
            priority: 40,
          },
          // Visualization stack — only referenced from lazy analytics/template
          // chunks, so this stays out of the initial page load.
          viz: {
            test: /[\\/]node_modules[\\/](recharts|leaflet|react-leaflet|force-graph|react-force-graph.*|d3-[^\\/]*|victory-vendor)[\\/]/,
            name: 'viz',
            chunks: 'all',
            priority: 30,
          },
          // PDF export — only referenced from the dynamic exportChatPdf import.
          pdf: {
            test: /[\\/]node_modules[\\/](jspdf|html2canvas|canvg|pako)[\\/]/,
            name: 'pdf',
            chunks: 'all',
            priority: 30,
          },
          // Markdown/remark pipeline used by the chat thread.
          markdown: {
            test: /[\\/]node_modules[\\/](react-markdown|remark[^\\/]*|rehype[^\\/]*|micromark[^\\/]*|unified|mdast[^\\/]*|hast[^\\/]*|unist[^\\/]*|vfile[^\\/]*)[\\/]/,
            name: 'markdown',
            chunks: 'all',
            priority: 30,
          },
          // `chunks: 'initial'` on purpose: a single named chunk with
          // `chunks: 'all'` would merge async-only vendors (handlebars, ajv…)
          // back into the eagerly-loaded bundle. Async-only vendor modules
          // instead stay inside their lazy chunks (default splitting).
          vendors: {
            test: /[\\/]node_modules[\\/]/,
            name: 'vendors',
            chunks: 'initial',
            priority: 10,
          },
        },
      },
    },
    devServer: {
      historyApiFallback: true,
      static: { directory: path.join(__dirname, 'public') },
      compress: true,
      host: '127.0.0.1',
      port: 4000,
      hot: true,
      open: false,
      proxy: [
        {
          context: ['/api'],
          target: API_URL,
          changeOrigin: true,
          secure: false,
          // Match prod nginx budget (5h) for SSE /api/chat/stream
          proxyTimeout: 18_000_000,
          timeout: 18_000_000,
        },
      ],
    },
  };
};
