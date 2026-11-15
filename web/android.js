/* Native MIDI adapter, installed before the unchanged SwayCommand engine. */
(() => {
  const access = { inputs: new Map(), outputs: new Map(), sysexEnabled: false, onstatechange: null };
  Object.defineProperty(navigator, 'requestMIDIAccess', { value: async () => access, configurable: true });
  const app = () => window.__swaycommand;
  const icon = paths => `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths}</svg>`;
  const icons = {
    menu: icon('<path d="M4 6h16M4 12h16M4 18h16"/>'),
    play: icon('<path d="m8 5 11 7-11 7Z"/>'),
    pause: icon('<path d="M8 5v14M16 5v14"/>'),
    next: icon('<path d="m4 6 7 6-7 6M13 6l7 6-7 6"/>'),
  };
  function setPlaying(playing) {
    const button = document.querySelector('#android-play');
    if (!button) return;
    button.innerHTML = playing ? icons.pause : icons.play;
    button.dataset.playing = String(playing);
    button.setAttribute('aria-label', playing ? 'Pause audio' : 'Play audio');
    button.title = playing ? 'Pause audio' : 'Play audio';
  }
  const panic = () => {
    const s = app()?.state;
    s?.synth?.allNotesOff();
    s?.sampler?.stopAll();
  };
  let portList = [];
  let messageCount = 0;
  let usbDetail = 'No MIDI input detected';
  function updateConnection() {
    const status = document.querySelector('#android-connection');
    if (!status) return;
    status.textContent = portList.length
      ? `CONNECTED: ${portList.map(p => p[1]).join(', ')} · ${messageCount} MIDI messages`
      : `NOT CONNECTED · ${usbDetail}`;
    status.dataset.connected = String(portList.length > 0);
    const link = document.querySelector('#android-link');
    if (link) { link.textContent = portList.length ? 'Sway connected' : 'Connect Sway'; link.dataset.connected = String(portList.length > 0); }
  }
  window.swayAndroid = {
    usbStatus(detail) { usbDetail = detail; updateConnection(); },
    ports(list) {
      if (JSON.stringify(list) !== JSON.stringify(portList)) messageCount = 0;
      portList = list;
      const ids = new Set(list.map(p => p[0]));
      for (const [id, port] of access.inputs) if (!ids.has(id)) {
        port.state = 'disconnected'; port.connection = 'closed'; access.inputs.delete(id);
      }
      for (const [id, name] of list) if (!access.inputs.has(id)) access.inputs.set(id, {
        id, name, manufacturer: 'USB MIDI', type: 'input', state: 'connected', connection: 'open', onmidimessage: null,
        async open() { return this; }, async close() { this.onmidimessage = null; return this; },
      });
      access.onstatechange?.({ target: access });
      updateConnection();
    },
    midi(batch) {
      for (const [id, bytes, timestamp] of batch) access.inputs.get(id)?.onmidimessage?.({ data: new Uint8Array(bytes), receivedTime: timestamp });
      messageCount += batch.filter(([id]) => access.inputs.has(id)).length;
      updateConnection();
    },
    panic,
    pause() {
      panic();
      const s = app()?.state;
      s?.transport?.stop();
      s?.audio?.ctx?.suspend();
      setPlaying(false);
    },
  };
  document.addEventListener('DOMContentLoaded', () => {
    const bar = document.createElement('div');
    bar.id = 'android-bar';
    bar.innerHTML = `<button id="android-menu" aria-label="Open controls" title="Menu" aria-expanded="false">${icons.menu}</button><button id="android-play" data-playing="false" aria-label="Play audio" title="Play audio">${icons.play}</button><button id="android-next" aria-label="Next scene" title="Next scene">${icons.next}</button>`;
    document.body.append(bar);
    const panel = document.createElement('section');
    panel.id = 'android-panel'; panel.hidden = true; panel.setAttribute('aria-label', 'Performance controls');
    panel.innerHTML = '<div class="android-tools"><button data-instrument="synth">Synth</button><button data-instrument="rack">Effects</button><button data-instrument="kit">Samples</button></div><label class="android-scene-label">Scene<select id="android-scenes"></select></label><div class="android-tools"><button id="android-view">Studio</button><button id="android-hide">Hide</button><button id="android-panic">Stop sound</button></div><details><summary id="android-link">Connect Sway</summary><p id="android-connection">Checking USB MIDI…</p><button id="android-recheck">Check USB</button></details>';
    document.body.append(panel);
    let hideTimer;
    const pointers = new Set();
    function scheduleHide() {
      clearTimeout(hideTimer);
      if (!panel.hidden || pointers.size) return;
      hideTimer = setTimeout(() => {
        // Keyboard users keep their focused controls; pointer users get a clear stage.
        if (bar.querySelector(':focus-visible')) return;
        bar.hidden = true;
      }, 1500);
    }
    function showControls() { bar.hidden = false; scheduleHide(); }
    function closePanel() { panel.hidden = true; document.querySelector('#android-menu').setAttribute('aria-expanded', 'false'); scheduleHide(); }
    document.addEventListener('pointerdown', event => {
      pointers.add(event.pointerId);
      clearTimeout(hideTimer);
      if (!panel.hidden && !panel.contains(event.target) && !document.querySelector('#android-menu').contains(event.target)) closePanel();
      const drawer = document.querySelector('#drawer');
      if (drawer?.classList.contains('open') && !drawer.contains(event.target)) document.querySelector('#drawer-close').click();
      showControls();
    }, true);
    const releasePointer = event => { pointers.delete(event.pointerId); scheduleHide(); };
    document.addEventListener('pointerup', releasePointer, true);
    document.addEventListener('pointercancel', releasePointer, true);
    document.addEventListener('keydown', event => {
      if (event.key === 'Escape') { closePanel(); document.querySelector('#drawer-close')?.click(); showControls(); }
      if (event.key === 'Tab') showControls();
    });
    bar.addEventListener('focusout', scheduleHide);
    bar.addEventListener('click', scheduleHide);
    document.querySelector('#android-menu').onclick = () => {
      panel.hidden = !panel.hidden;
      scheduleHide();
      document.querySelector('#android-menu').setAttribute('aria-expanded', String(!panel.hidden));
      if (!panel.hidden) {
        const select = document.querySelector('#android-scenes'); select.replaceChildren();
        for (const scene of app()?.state.engine.sceneList || []) {
          const option = document.createElement('option'); option.value = scene.id; option.textContent = scene.name; select.append(option);
        }
        const current = document.querySelector('#scene-bank .current');
        if (current?.dataset.scene) select.value = current.dataset.scene;
      }
    };
    document.querySelector('#android-hide').onclick = () => { closePanel(); clearTimeout(hideTimer); bar.hidden = true; };
    document.querySelector('#android-next').onclick = () => app()?.state.engine.nextScene();
    document.querySelector('#android-scenes').onchange = event => {
      const engine = app()?.state.engine;
      if (engine) { engine.autoVJ.enabled = false; engine.setScene(event.target.value); }
      closePanel();
    };
    panel.querySelectorAll('[data-instrument]').forEach(button => button.onclick = () => { app()?.openStudio(button.dataset.instrument); closePanel(); });
    document.querySelector('#android-recheck').onclick = () => window.SwayNative?.recheckMidi();
    document.querySelector('#android-play').onclick = async () => {
      const a = app();
      if (!a) return;
      if (document.querySelector('#android-play').dataset.playing === 'true') { window.swayAndroid.pause(); return; }
      window.SwayNative?.requestAudioFocus();
      await a.state.audio.ctx.resume();
      a.router.synthEnabled = true;
      a.state.synthEnabled = true;
      const check = document.querySelector('#synth-enable');
      if (check) check.checked = true;
      setPlaying(true);
    };
    document.querySelector('#android-panic').onclick = () => window.swayAndroid.pause();
    document.querySelector('#android-view').onclick = event => {
      const studio = document.body.classList.toggle('android-studio');
      event.target.textContent = studio ? 'Easy mode' : 'Studio';
      closePanel();
    };
    window.swayAndroid.ports(portList);
    scheduleHide();
    // Wait for the browser bridge to be installed before customising platform-only features.
    const timer = setInterval(() => {
      const bridge = window.swaycommand;
      if (!bridge) return;
      clearInterval(timer);
      bridge.doctor.run = async () => [{ id: 'android', label: 'Android USB MIDI', status: 'ok', detail: 'Connect Sway through a powered USB hub. Tap Play to start. USB details are inside Menu.' }];
      bridge.platform.systemAudio = async () => ({ supported: false, detail: 'Use imported audio or microphone input on Android.' });
    }, 20);
  });
})();
