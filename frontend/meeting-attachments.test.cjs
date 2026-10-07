const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');

const scripts = path.join(__dirname, '..', 'src', 'main', 'webapp', 'js');

test('agenda polling updates attachments before/during/after a session and removes detached links', async () => {
  for (const status of ['FINALIZED', 'IN_SESSION', 'COMPLETED']) {
    let poll;
    let payload = {
      meetingStatus: status,
      items: [{ agendaItemId: 20, isCurrent: true, attachmentsHtml: '<p>slides.pptx</p>',
        notesHtml: 'Notes', outcomesHtml: 'Outcomes' }]
    };
    const elements = {
      'agenda-live-config': { textContent: JSON.stringify({ stateUrl: '/hub/es/agenda?action=state',
        meetingStatus: status }) },
      'agenda-attachments-20': { innerHTML: '' },
      'agenda-notes-20': { innerHTML: '' },
      'agenda-outcomes-20': { innerHTML: '' }
    };
    let current;
    let stopped = false;
    vm.runInNewContext(fs.readFileSync(path.join(scripts, 'agenda-live.js'), 'utf8'), {
      document: {
        getElementById: id => elements[id],
        querySelector: () => ({ classList: { toggle: (_name, value) => { current = value; } } })
      },
      fetch: async () => ({ ok: true, json: async () => payload }),
      setInterval: callback => { poll = callback; return 1; },
      clearInterval: () => { stopped = true; }
    });
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(elements['agenda-attachments-20'].innerHTML, '<p>slides.pptx</p>');
    assert.equal(elements['agenda-notes-20'].innerHTML, 'Notes');
    assert.equal(current, status === 'IN_SESSION');
    payload.items[0].attachmentsHtml = '';
    poll();
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(elements['agenda-attachments-20'].innerHTML, '');
    payload.meetingStatus = 'CLOSED';
    poll();
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(stopped, true);
  }
});

test('attachment submission only replaces its own panel and preserves the editor', async () => {
  let submit;
  const message = { textContent: '', className: '' };
  const button = { disabled: false };
  const editor = { unsavedNotes: 'My unsaved note draft' };
  const panel = {
    innerHTML: 'Old panel',
    querySelector: () => message,
    addEventListener: (_event, callback) => { submit = callback; }
  };
  const form = {
    action: '/hub/es/meeting-workspace?meetingId=10&itemId=20&action=uploadAttachment',
    querySelector: () => button
  };
  let posted;
  vm.runInNewContext(fs.readFileSync(path.join(scripts, 'meeting-workspace-attachments.js'), 'utf8'), {
    document: { querySelector: () => panel },
    FormData: class { get() { return 'csrf'; } },
    fetch: async (url, options) => {
      posted = { url, options };
      return { ok: true, headers: { get: () => 'application/json' },
        json: async () => ({ html: 'New panel', message: 'Attachment uploaded.' }) };
    }
  });
  let prevented = false;
  submit({ target: { closest: () => form }, preventDefault: () => { prevented = true; } });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(prevented, true);
  assert.equal(posted.url, form.action);
  assert.equal(posted.options.headers['X-CSRF-Token'], 'csrf');
  assert.equal(panel.innerHTML, 'New panel');
  assert.equal(editor.unsavedNotes, 'My unsaved note draft');
  assert.equal(message.textContent, 'Attachment uploaded.');
  assert.equal(button.disabled, false);
});

test('attachment errors do not replace the panel or navigate away', async () => {
  let submit;
  const message = {};
  const button = {};
  const panel = { innerHTML: 'Existing attachments', querySelector: () => message,
    addEventListener: (_event, callback) => { submit = callback; } };
  vm.runInNewContext(fs.readFileSync(path.join(scripts, 'meeting-workspace-attachments.js'), 'utf8'), {
    document: { querySelector: () => panel },
    FormData: class { get() { return 'csrf'; } },
    fetch: async () => ({ ok: false, headers: { get: () => 'application/json' },
      json: async () => ({ error: 'Meeting is closed.' }) })
  });
  submit({ target: { closest: () => ({ action: '/workspace', querySelector: () => button }) },
    preventDefault() {} });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(panel.innerHTML, 'Existing attachments');
  assert.equal(message.textContent, 'Meeting is closed.');
  assert.equal(button.disabled, false);
});
