const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
test('native ports hot-plug, deliver raw notes, and disconnect', async () => {
  const status = { textContent: '', dataset: {} };
  const context = { navigator: {}, window: {}, document: { addEventListener() {}, querySelector(selector) { return selector === '#android-connection' ? status : null; } }, Uint8Array, Map, Set };
  vm.runInNewContext(fs.readFileSync('web/android.js', 'utf8'), context);
  const access = await context.navigator.requestMIDIAccess();
  let changes = 0;
  access.onstatechange = () => changes++;
  context.window.swayAndroid.ports([['1:0', 'Audima Labs The Sway']]);
  const port = access.inputs.get('1:0');
  let note;
  port.onmidimessage = e => note = Array.from(e.data);
  context.window.swayAndroid.midi([['1:0', [144, 24, 100], 123]]);
  assert.deepEqual(note, [144, 24, 100]);
  assert.match(status.textContent, /CONNECTED: Audima Labs The Sway · 1 MIDI messages/);
  assert.equal(status.dataset.connected, 'true');
  context.window.swayAndroid.ports([]);
  assert.equal(access.inputs.size, 0);
  assert.equal(port.state, 'disconnected');
  assert.equal(changes, 2);
  context.window.swayAndroid.usbStatus('USB seen: Sway; no open MIDI input');
  assert.match(status.textContent, /NOT CONNECTED · USB seen: Sway/);
  assert.equal(status.dataset.connected, 'false');
});
test('pause stops voices and transport before suspending audio', () => {
  const calls = [];
  const context = { navigator: {}, window: { __swaycommand: { state: {
    synth: { allNotesOff: () => calls.push('synth') }, sampler: { stopAll: () => calls.push('sampler') },
    transport: { stop: () => calls.push('transport') }, audio: { ctx: { suspend: () => calls.push('suspend') } },
  } } }, document: { addEventListener() {}, querySelector() { return null; } }, Uint8Array, Map, Set };
  vm.runInNewContext(fs.readFileSync('web/android.js', 'utf8'), context);
  context.window.swayAndroid.pause();
  assert.deepEqual(calls, ['synth', 'sampler', 'transport', 'suspend']);
});
