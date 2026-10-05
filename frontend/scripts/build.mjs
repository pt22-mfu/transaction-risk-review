import { build } from 'esbuild';
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
await rm('dist', { recursive: true, force: true });
await mkdir('dist/assets', { recursive: true });
await build({ entryPoints: ['src/main.tsx'], bundle: true, minify: true, jsx: 'automatic',
  define: { 'process.env.NODE_ENV': '"production"' }, target: ['es2022'],
  outdir: 'dist/assets', entryNames: 'app', logLevel: 'info' });
const html = (await readFile('index.html', 'utf8'))
  .replace('/src/main.tsx', '/assets/app.js')
  .replace('</head>', '<link rel="stylesheet" href="/assets/app.css"/></head>');
await writeFile('dist/index.html', html);
console.log('Production dashboard built.');
