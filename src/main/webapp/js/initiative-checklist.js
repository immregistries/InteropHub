(function () {
  'use strict';

  var root = document.querySelector('[data-checklist]');
  if (!root) {
    return;
  }

  var dragged = null;

  document.addEventListener('click', function (event) {
    var hide = event.target.closest('[data-hide-done]');
    if (hide && root.contains(hide)) {
      event.preventDefault();
      toggleDone(hide);
      return;
    }
    var opener = event.target.closest('[data-dialog-open]');
    if (opener && root.contains(opener)) {
      var dialog = document.getElementById(opener.getAttribute('data-dialog-open'));
      if (dialog && typeof dialog.showModal === 'function' && !dialog.open) {
        dialog.showModal();
      }
    }
    var closer = event.target.closest('[data-dialog-close]');
    if (closer) {
      var closing = document.getElementById(closer.getAttribute('data-dialog-close'));
      if (closing) {
        closing.close();
      }
    }
    var text = event.target.closest('.initiative-checklist-text, .initiative-checklist-phase-name');
    if (text && root.contains(text)) {
      beginEdit(text);
    }
  });

  document.addEventListener('submit', function (event) {
    var form = event.target;
    if (!form.matches || !form.matches('[data-checklist-save]') || !root.contains(form)) {
      return;
    }
    event.preventDefault();
    save(form);
  });

  document.addEventListener('change', function (event) {
    var target = event.target;
    var form = target.closest ? target.closest('form[data-checklist-save]') : null;
    if (!form || !root.contains(form)) {
      return;
    }
    if (target.type === 'checkbox') {
      var status = form.querySelector('[name="itemStatus"]');
      if (status) {
        status.value = target.checked ? 'DONE' : 'OPEN';
      }
      submit(form);
    } else if (target.name === 'assignedUserId') {
      submit(form);
    }
  });

  document.addEventListener('keydown', function (event) {
    var editor = event.target;
    if (editor.classList && editor.classList.contains('initiative-checklist-editor')) {
      if (event.key === 'Enter') {
        event.preventDefault();
        submit(editor.closest('form'));
      } else if (event.key === 'Escape') {
        event.preventDefault();
        cancelEdit(editor);
      }
      return;
    }
    var handle = event.target.closest ? event.target.closest('.initiative-checklist-handle') : null;
    if (!handle || !root.contains(handle)) {
      return;
    }
    if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
      event.preventDefault();
      keyboardMove(handle, event.key === 'ArrowUp' ? 'up' : 'down');
    }
  });

  document.addEventListener('focusout', function (event) {
    var editor = event.target;
    if (!editor.classList || !editor.classList.contains('initiative-checklist-editor') || !root.contains(editor)) {
      return;
    }
    if (editor.dataset.cancel === '1') {
      editor.dataset.cancel = '';
      return;
    }
    if ((editor.dataset.original || '') === editor.value) {
      closeEdit(editor);
      return;
    }
    submit(editor.closest('form'));
  });

  root.addEventListener('dragstart', function (event) {
    var handle = event.target.closest('.initiative-checklist-handle');
    if (!handle) {
      return;
    }
    dragged = handle.closest('tr');
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'move';
      event.dataTransfer.setData('text/plain', dragged.getAttribute('data-item-id') || '');
    }
  });

  root.addEventListener('dragend', function () {
    dragged = null;
    clearDrop();
  });

  root.addEventListener('dragover', function (event) {
    var row = dropRow(event.target);
    if (!dragged || !row || row === dragged) {
      return;
    }
    event.preventDefault();
    clearDrop();
    row.classList.add('is-drop-target');
  });

  root.addEventListener('drop', function (event) {
    var row = dropRow(event.target);
    if (!dragged || !row || row === dragged) {
      return;
    }
    event.preventDefault();
    clearDrop();
    var place = dropPlace(row);
    if (!place || place.beforeId === dragged.getAttribute('data-item-id')) {
      return;
    }
    var parent = dragged.parentNode;
    var next = dragged.nextSibling;
    place.insertBefore(dragged);
    dragged.setAttribute('data-phase', place.phase);
    var body = new URLSearchParams();
    body.set('action', 'reorder-item');
    body.set('itemId', dragged.getAttribute('data-item-id'));
    body.set('beforeItemId', place.beforeId || '');
    body.set('phase', place.phase || '');
    var moving = dragged;
    post(postUrl(), body).then(function (json) {
      if (!json.ok) {
        throw new Error(json.error || 'Could not save that change.');
      }
      paintProgress(json);
      note(moving.querySelector('form') || moving, 'Saved', false);
    }).catch(function (err) {
      parent.insertBefore(moving, next);
      note(moving.querySelector('form') || moving, err.message, true);
    });
    dragged = null;
  });

  function submit(form) {
    if (form && typeof form.requestSubmit === 'function') {
      form.requestSubmit();
    }
  }

  function beginEdit(button) {
    var form = button.closest('form');
    var editor = form ? form.querySelector('.initiative-checklist-editor') : null;
    var row = button.closest('tr');
    if (!editor || !row) {
      return;
    }
    editor.dataset.original = editor.value;
    editor.hidden = false;
    row.classList.add('is-editing');
    editor.focus();
    editor.select();
  }

  function cancelEdit(editor) {
    editor.dataset.cancel = '1';
    editor.value = editor.dataset.original || editor.value;
    closeEdit(editor);
  }

  function closeEdit(editor) {
    editor.hidden = true;
    var row = editor.closest('tr');
    if (row) {
      row.classList.remove('is-editing');
    }
  }

  function save(form) {
    if (form.dataset.saving === '1') {
      return;
    }
    form.dataset.saving = '1';
    var body = new URLSearchParams(new FormData(form));
    post(form.getAttribute('action'), body).then(function (json) {
      form.dataset.saving = '';
      if (!json.ok) {
        throw new Error(json.error || 'Could not save that change.');
      }
      apply(form, json);
      note(form, 'Saved', false);
      window.setTimeout(function () {
        var live = form.querySelector('.initiative-checklist-note');
        if (live && live.textContent === 'Saved') {
          live.textContent = '';
        }
      }, 1500);
    }).catch(function (err) {
      form.dataset.saving = '';
      var box = form.querySelector('input[type="checkbox"]');
      var status = form.querySelector('[name="itemStatus"]');
      if (box && status) {
        box.checked = status.value === 'OPEN';
      }
      note(form, (err && err.message) ? err.message : 'Could not save that change.', true);
      var editor = form.querySelector('.initiative-checklist-editor');
      if (editor && editor.name === 'title') {
        editor.hidden = false;
        var row = form.closest('tr');
        if (row) {
          row.classList.add('is-editing');
        }
      }
    });
  }

  function apply(form, json) {
    paintProgress(json);
    if (json.html) {
      var addRow = form.closest('tr');
      addRow.insertAdjacentHTML('beforebegin', json.html);
      var dialogs = document.getElementById('checklist-dialogs');
      if (dialogs && json.dialog) {
        dialogs.insertAdjacentHTML('beforeend', json.dialog);
      }
      var title = form.querySelector('[name="title"]');
      if (title) {
        title.value = '';
      }
      return;
    }
    var action = (form.querySelector('[name="action"]') || {}).value;
    var row = itemRow(json.itemId);
    if (action === 'rename-phase') {
      var editor = form.querySelector('.initiative-checklist-editor');
      var name = editor ? editor.value : '';
      var phaseRow = form.closest('tr');
      renamePhase(phaseRow, name);
      if (editor) {
        editor.dataset.original = name;
        closeEdit(editor);
      }
      return;
    }
    if (!row) {
      return;
    }
    if (action === 'remove-item' || action === 'restore-item') {
      paintRemoved(row, json, action);
    }
    if (json.title) {
      var label = row.querySelector('.initiative-checklist-text');
      if (label) {
        label.textContent = json.title;
      }
      var titleInput = row.querySelector('[name="title"]');
      if (titleInput && !row.classList.contains('is-editing')) {
        titleInput.value = json.title;
      }
      var heading = document.querySelector('#item-' + json.itemId + '-dialog .aira-dialog__title');
      if (heading) {
        heading.textContent = json.title;
      }
    }
    if (json.guidance !== undefined && json.guidance !== null) {
      var guide = row.querySelector('[name="guidance"]');
      if (guide) {
        guide.value = json.guidance;
      }
    }
    if (json.status) {
      paintStatus(row, json);
      var select = document.querySelector('#item-' + json.itemId + '-dialog [name="itemStatus"]');
      if (select) {
        select.value = json.status;
      }
    }
    paintDue(row, json);
    var editor = form.querySelector('.initiative-checklist-editor');
    if (editor) {
      editor.dataset.original = editor.value;
      closeEdit(editor);
    }
  }

  function paintRemoved(row, json, action) {
    var showing = root.getAttribute('data-show-removed') === 'true';
    if (action === 'remove-item' && !showing) {
      var dialog = document.getElementById('item-' + json.itemId + '-dialog');
      if (dialog) {
        dialog.remove();
      }
      row.remove();
      return;
    }
    row.setAttribute('data-removed', json.removed ? 'true' : 'false');
    var button = row.querySelector('button[aria-label="Remove"], button[aria-label="Restore"]');
    var hidden = row.querySelector('input[name="action"][value="remove-item"], input[name="action"][value="restore-item"]');
    if (button && hidden) {
      var restore = action === 'remove-item';
      hidden.value = restore ? 'restore-item' : 'remove-item';
      button.textContent = restore ? 'Restore' : '×';
      button.setAttribute('aria-label', restore ? 'Restore' : 'Remove');
    }
  }

  function paintStatus(row, json) {
    row.setAttribute('data-status', json.status);
    row.classList.toggle('aira-muted', json.status === 'DONE' || json.status === 'NOT_APPLICABLE');
    var box = row.querySelector('input[type="checkbox"]');
    if (box) {
      box.checked = json.status === 'DONE';
    }
    var hidden = row.querySelector('input[name="itemStatus"]');
    if (hidden) {
      hidden.value = json.status === 'DONE' ? 'OPEN' : 'DONE';
    }
    var hide = root.querySelector('[data-hide-done]');
    if (hide && hide.getAttribute('aria-pressed') === 'true') {
      row.hidden = json.status === 'DONE';
    }
  }

  function paintDue(row, json) {
    var cell = row.querySelector('.initiative-checklist-due');
    if (!cell || json.due === undefined) {
      return;
    }
    var text = cell.querySelector('.initiative-checklist-due-text');
    if (text) {
      text.textContent = json.due || '';
    }
    var override = cell.querySelector('.initiative-checklist-override');
    if (json.override) {
      if (!override) {
        override = document.createElement('span');
        override.className = 'aira-meta initiative-checklist-override';
        override.textContent = 'override';
        cell.appendChild(override);
      }
    } else if (override) {
      override.remove();
    }
    var badge = cell.querySelector('.initiative-checklist-urgency');
    if (json.urgency === 'OVERDUE' || json.urgency === 'DUE_SOON') {
      if (!badge) {
        badge = document.createElement('span');
        badge.className = 'aira-badge initiative-checklist-urgency';
        cell.appendChild(badge);
      }
      badge.classList.toggle('aira-badge--danger', json.urgency === 'OVERDUE');
      badge.classList.toggle('aira-badge--warning', json.urgency === 'DUE_SOON');
      badge.textContent = json.urgency === 'OVERDUE' ? 'Overdue' : 'Due soon';
    } else if (badge) {
      badge.remove();
    }
  }

  function paintProgress(json) {
    var line = document.getElementById('checklist-progress');
    if (line && json.progress) {
      line.textContent = json.progress;
    }
  }

  function renamePhase(phaseRow, name) {
    if (!phaseRow) {
      return;
    }
    var previous = phaseRow.getAttribute('data-phase');
    phaseRow.setAttribute('data-phase', name);
    var button = phaseRow.querySelector('.initiative-checklist-phase-name');
    if (button) {
      button.textContent = name;
    }
    var from = phaseRow.querySelector('[name="fromPhase"]');
    if (from) {
      from.value = name;
    }
    var row = phaseRow.nextElementSibling;
    while (row && !row.classList.contains('initiative-checklist-phase')) {
      if (row.getAttribute('data-phase') === previous) {
        row.setAttribute('data-phase', name);
        var phase = row.querySelector('[name="phase"]');
        if (phase) {
          phase.value = name;
        }
      }
      row = row.nextElementSibling;
    }
  }

  function toggleDone(link) {
    var pressed = link.getAttribute('aria-pressed') !== 'true';
    link.setAttribute('aria-pressed', pressed ? 'true' : 'false');
    link.textContent = pressed ? 'Show done' : 'Hide done';
    root.querySelectorAll('.initiative-checklist-row').forEach(function (row) {
      if (row.getAttribute('data-status') === 'DONE') {
        row.hidden = pressed;
      }
    });
    var destination = new URL(link.href, window.location.href);
    var shown = destination.pathname + destination.search;
    window.history.replaceState(null, '', shown);
    var back = new URL(destination.href);
    if (back.searchParams.get('hideDone') === '1') {
      back.searchParams.delete('hideDone');
    } else {
      back.searchParams.set('hideDone', '1');
    }
    link.href = back.pathname + back.search;
    var removed = root.querySelector('[data-removed-toggle]');
    if (removed) {
      var removedUrl = new URL(removed.href, window.location.href);
      if (pressed) {
        removedUrl.searchParams.set('hideDone', '1');
      } else {
        removedUrl.searchParams.delete('hideDone');
      }
      removed.href = removedUrl.pathname + removedUrl.search;
    }
  }

  function keyboardMove(handle, direction) {
    var row = handle.closest('tr');
    var neighbor = direction === 'up' ? previousItem(row) : nextItem(row);
    if (!neighbor || neighbor.getAttribute('data-phase') !== row.getAttribute('data-phase')) {
      return;
    }
    if (direction === 'up') {
      neighbor.before(row);
    } else {
      neighbor.after(row);
    }
    var body = new URLSearchParams();
    body.set('action', 'move-item');
    body.set('itemId', row.getAttribute('data-item-id'));
    body.set('direction', direction);
    post(postUrl(), body).then(function (json) {
      if (!json.ok) {
        throw new Error(json.error || 'Could not save that change.');
      }
      paintProgress(json);
      handle.focus();
    }).catch(function (err) {
      if (direction === 'up') {
        neighbor.after(row);
      } else {
        neighbor.before(row);
      }
      note(row.querySelector('form'), err.message, true);
      handle.focus();
    });
  }

  function dropRow(target) {
    var row = target.closest ? target.closest('tr') : null;
    if (!row || !root.contains(row)) {
      return null;
    }
    if (row.classList.contains('initiative-checklist-row')
        || row.classList.contains('initiative-checklist-add')
        || row.classList.contains('initiative-checklist-phase')) {
      return row;
    }
    return null;
  }

  function dropPlace(row) {
    var phase = row.getAttribute('data-phase');
    if (row.classList.contains('initiative-checklist-row')) {
      return { phase: phase, beforeId: row.getAttribute('data-item-id'), insertBefore: function (moving) {
        row.parentNode.insertBefore(moving, row);
      } };
    }
    if (row.classList.contains('initiative-checklist-add')) {
      var following = nextItem(row);
      return { phase: phase, beforeId: following ? following.getAttribute('data-item-id') : '', insertBefore: function (moving) {
        row.parentNode.insertBefore(moving, row);
      } };
    }
    var first = nextItem(row);
    return { phase: phase, beforeId: first && first.getAttribute('data-phase') === phase ? first.getAttribute('data-item-id') : '', insertBefore: function (moving) {
      var anchor = nextItem(row);
      if (anchor && anchor.getAttribute('data-phase') === phase) {
        anchor.parentNode.insertBefore(moving, anchor);
      } else {
        row.parentNode.insertBefore(moving, row.nextSibling);
      }
    } };
  }

  function previousItem(row) {
    var cursor = row.previousElementSibling;
    while (cursor && !cursor.classList.contains('initiative-checklist-row')) {
      cursor = cursor.previousElementSibling;
    }
    return cursor;
  }

  function nextItem(row) {
    var cursor = row.nextElementSibling;
    while (cursor && !cursor.classList.contains('initiative-checklist-row')) {
      cursor = cursor.nextElementSibling;
    }
    return cursor;
  }

  function itemRow(id) {
    return id ? document.getElementById('item-' + id) : null;
  }

  function clearDrop() {
    root.querySelectorAll('.is-drop-target').forEach(function (row) {
      row.classList.remove('is-drop-target');
    });
  }

  function postUrl() {
    var form = root.querySelector('form[data-checklist-save]');
    return form ? String(form.getAttribute('action') || '').split('#')[0] : window.location.pathname;
  }

  function post(url, body) {
    var target = String(url || '').split('#')[0];
    return fetch(target, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
        'Accept': 'application/json'
      },
      body: body.toString()
    }).then(function (response) {
      return response.json().catch(function () {
        throw new Error('Could not save that change.');
      });
    });
  }

  function note(form, message, error) {
    if (!form || !form.querySelector) {
      return;
    }
    var live = form.querySelector('.initiative-checklist-note');
    if (!live) {
      return;
    }
    live.textContent = message || '';
    live.classList.toggle('aira-danger-text', !!error);
  }
})();
