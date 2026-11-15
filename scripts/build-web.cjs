const fs = require('node:fs');
const path = require('node:path');
const esbuild = require('esbuild');
const root = path.resolve(__dirname, '..');
const source = path.join(root, 'vendor/swaycommand');
const out = path.join(root, 'android/assets/www');
fs.mkdirSync(out, { recursive: true });
const copy = (from, to) => fs.cpSync(path.join(source, from), path.join(out, to), { recursive: true });
async function build() {
  await esbuild.build({
    entryPoints: [path.join(source, 'src/renderer/embed.js')],
    bundle: true, format: 'iife', platform: 'browser', target: 'chrome110',
    outfile: path.join(out, 'embed.bundle.js'), minify: true, legalComments: 'eof',
    define: { __SWAY_EMBED_BUILD__: JSON.stringify({ name: 'SwayCommand Android', version: '0.1.0', sha: '5fb092d3641f2d279f5ca13b010e96a370d9d32c' }) },
  });
  let html = fs.readFileSync(path.join(source, 'src/renderer/index.html'), 'utf8');
  html = html.replace('<head>', '<head>\n<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">')
    .replace('frame-src gan:', "frame-src 'none'")
    .replace('./renderer.bundle.js', './embed.bundle.js')
    .replace('</head>', '<link rel="stylesheet" href="./android.css">\n<script src="./android.js"></script>\n</head>');
  fs.writeFileSync(path.join(out, 'index.html'), html);
  copy('src/renderer/styles.css', 'styles.css');
  copy('src/renderer/audio/dsp.worklet.js', 'dsp.worklet.js');
  copy('src/renderer/fonts', 'fonts');
  copy('projects/templates', 'templates');
  copy('docs', 'docs/docs');
  copy('docs/media', 'media');
  copy('README.md', 'docs/README.md');
  copy('LICENSE', 'LICENSE');
  const docs = ['README.md', ...fs.readdirSync(path.join(source, 'docs')).filter(f => f.endsWith('.md')).map(f => 'docs/' + f)];
  fs.writeFileSync(path.join(out, 'docs-index.json'), JSON.stringify(docs.map(id => ({ id, title: path.basename(id, '.md') }))));
  for (const file of ['android.js', 'android.css']) fs.copyFileSync(path.join(root, 'web', file), path.join(out, file));
  console.log('Android web engine built.');
}
build().catch(error => { console.error(error); process.exitCode = 1; });
