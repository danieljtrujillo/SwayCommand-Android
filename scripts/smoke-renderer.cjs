// Runs with Electron; checks the bundled engine at a phone-sized viewport.
const { app, BrowserWindow } = require('electron');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../android/assets/www');
app.setPath('userData', path.resolve(__dirname, '../build/smoke-profile-' + Date.now()));
app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required');
const server = http.createServer((request, response) => {
  const name = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
  const file = path.resolve(root, '.' + (name === '/' ? '/index.html' : name));
  if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { response.writeHead(404); response.end(); return; }
  const types = { '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css', '.json': 'application/json', '.sway': 'application/json' };
  response.setHeader('Content-Type', types[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(response);
});
app.whenReady().then(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const window = new BrowserWindow({ show: false, width: 915, height: 412, useContentSize: true, webPreferences: { contextIsolation: true, nodeIntegration: false, offscreen: true, backgroundThrottling: false } });
  window.webContents.session.setPermissionRequestHandler((_contents, _permission, callback) => callback(false));
  window.webContents.on('console-message', (_event, ...args) => {
    const text = args.map(a => typeof a === 'object' ? a.message || '' : a).join(' ');
    if (/error|failed|ReferenceError|TypeError/i.test(text)) console.log('PAGE:', text);
  });
  try {
    await window.loadURL(`http://127.0.0.1:${server.address().port}/?autoplay=first-flight`);
    for (let attempt = 0; attempt < 100; attempt++) {
      if (await window.webContents.executeJavaScript('!!window.__swaycommand?.state?.entered')) break;
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    const result = await window.webContents.executeJavaScript(`(async () => {
      const a = window.__swaycommand;
      if (!a) throw Error('Engine failed to boot');
      for (let i = 0; i < 100 && document.querySelector('#btn-enter').disabled; i++) await new Promise(resolve => setTimeout(resolve, 100));
      if (document.querySelector('#btn-enter').disabled) throw Error('Startup checks never finished');
      document.querySelector('#btn-enter').click();
      window.swayAndroid.ports([['test:0', 'Audima Labs The Sway']]);
      document.querySelector('#android-play').click();
      let notes = 0;
      const noteOn = a.state.synth.noteOn;
      a.state.synth.noteOn = (...args) => { notes++; return noteOn(...args); };
      window.swayAndroid.midi([['test:0', [176, 50, 100], 1], ['test:0', [144, 24, 100], 2]]);
      const result = { connected: a.state.midi.control.connected, isSway: a.state.midi.control.isSway,
        x: a.state.midi.control.xy.x, pad: a.state.midi.control.lastPad,
        synth: a.router.synthEnabled, notes, audio: a.state.audio.ctx.state,
        canvas: [document.querySelector('#stage').width, document.querySelector('#stage').height] };
      if (!result.connected || !result.isSway || result.x !== 100/127 || result.pad !== 0 || !result.synth || notes !== 1) throw Error(JSON.stringify(result));
      window.swayAndroid.midi([['test:0', [128, 24, 0], 3]]);
      window.swayAndroid.ports([]);
      if (a.state.midi.control.connected) throw Error('Disconnect did not propagate');
      return result;
    })()`);
    fs.mkdirSync(path.resolve(__dirname, '../build'), { recursive: true });
    for (const [width, height, name] of [[915, 412, 'landscape'], [412, 915, 'portrait']]) {
      window.setContentSize(width, height);
      await new Promise(resolve => setTimeout(resolve, 400));
      await window.webContents.executeJavaScript(`(() => {
        document.body.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, pointerId: 1 }));
        document.body.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, pointerId: 1 }));
        const stage = document.querySelector('#stagecell').getBoundingClientRect();
        if (Math.abs(stage.width - innerWidth) > 1 || Math.abs(stage.height - innerHeight) > 1) throw Error('Stage does not fill viewport');
        const bar = document.querySelector('#android-bar').getBoundingClientRect();
        if (bar.left < 0 || bar.right > innerWidth || bar.bottom > innerHeight) throw Error('Controls overflow');
        document.querySelector('#android-menu').click();
        const panel = document.querySelector('#android-panel').getBoundingClientRect();
        if (panel.left < 0 || panel.right > innerWidth || panel.top < 0 || panel.bottom > innerHeight) throw Error('Menu overflows');
      })()`);
      await new Promise(resolve => setTimeout(resolve, 200));
      fs.writeFileSync(path.resolve(__dirname, '../build/easy-menu-' + name + '.png'), (await window.webContents.capturePage()).toPNG());
      await window.webContents.executeJavaScript(`(async () => {
        await new Promise(r => setTimeout(r, 1750));
        if (document.querySelector('#android-panel').hidden || document.querySelector('#android-bar').hidden) throw Error('Menu hid while in use');
        document.body.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, pointerId: 2 }));
        document.body.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, pointerId: 2 }));
        if (!document.querySelector('#android-panel').hidden) throw Error('Outside tap did not dismiss menu');
        await new Promise(r => setTimeout(r, 1750));
        if (!document.querySelector('#android-bar').hidden) throw Error('Controls did not auto-hide');
        document.body.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, pointerId: 3 }));
        document.body.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, pointerId: 3 }));
        if (document.querySelector('#android-bar').hidden) throw Error('Tap did not restore controls');
        for (const id of ['android-menu', 'android-play', 'android-next']) {
          const b = document.getElementById(id);
          if (!b.querySelector('svg') || !b.getAttribute('aria-label') || b.textContent.trim()) throw Error('Missing accessible icon: ' + id);
        }
      })()`);
      await new Promise(resolve => setTimeout(resolve, 200));
      fs.writeFileSync(path.resolve(__dirname, '../build/easy-' + name + '.png'), (await window.webContents.capturePage()).toPNG());
    }
    console.log('Portrait/landscape: full-screen layout, icon labels, 1.5s auto-hide, tap restore, outside dismissal, and open-menu persistence passed.');
    fs.writeFileSync(path.resolve(__dirname, '../build/phone-preview.png'), (await window.webContents.capturePage()).toPNG());
    const storedPath = await window.webContents.executeJavaScript(`(async () => {
      window.SwayNative = { exportProject() {} };
      const p = window.swaycommand.files.pathOf(new File([new Uint8Array([1,2,3,4])], 'persistence-test.wav'));
      await window.swaycommand.project.write('swayproject:/persistence-test.sway', {});
      return p;
    })()`);
    await window.loadURL(`http://127.0.0.1:${server.address().port}/`);
    const persisted = await window.webContents.executeJavaScript(`(async () => {
      window.SwayNative = { exportProject() {} };
      return Array.from(await window.swaycommand.files.readAudio(${JSON.stringify(storedPath)}));
    })()`);
    if (JSON.stringify(persisted) !== '[1,2,3,4]') throw Error('Media did not survive page restart');
    console.log('Imported media survives page restart.');
    window.setSize(412, 915);
    await new Promise(resolve => setTimeout(resolve, 500));
    fs.writeFileSync(path.resolve(__dirname, '../build/phone-portrait.png'), (await window.webContents.capturePage()).toPNG());
    console.log(JSON.stringify(result));
    window.destroy(); server.close(); app.exit(0);
  } catch (error) { console.error(error); server.close(); app.exit(1); }
});
