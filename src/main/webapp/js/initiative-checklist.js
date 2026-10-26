(function () {
  'use strict';

  document.querySelectorAll('[data-add-after]').forEach(function (button) {
    button.addEventListener('click', function () {
      var dialog = document.getElementById('add-item');
      if (!dialog || typeof dialog.showModal !== 'function') {
        return;
      }
      var after = dialog.querySelector('[name="afterItemId"]');
      var phase = dialog.querySelector('[name="phase"]');
      if (after) {
        after.value = button.getAttribute('data-add-after') || '';
      }
      if (phase) {
        phase.value = button.getAttribute('data-phase') || '';
      }
      if (!dialog.open) {
        dialog.showModal();
      }
    });
  });

  var draggedId = null;
  document.querySelectorAll('.initiative-checklist-handle').forEach(function (handle) {
    handle.addEventListener('dragstart', function (event) {
      var row = handle.closest('tr');
      draggedId = row ? row.getAttribute('data-item-id') : null;
      if (event.dataTransfer) {
        event.dataTransfer.effectAllowed = 'move';
        event.dataTransfer.setData('text/plain', draggedId || '');
      }
    });
    handle.addEventListener('dragend', function () {
      draggedId = null;
      document.querySelectorAll('.initiative-checklist-row.is-drop-target').forEach(function (row) {
        row.classList.remove('is-drop-target');
      });
    });
  });

  document.querySelectorAll('.initiative-checklist-row').forEach(function (row) {
    row.addEventListener('dragover', function (event) {
      if (!draggedId || row.getAttribute('data-item-id') === draggedId) {
        return;
      }
      event.preventDefault();
      row.classList.add('is-drop-target');
    });
    row.addEventListener('dragleave', function () {
      row.classList.remove('is-drop-target');
    });
    row.addEventListener('drop', function (event) {
      event.preventDefault();
      row.classList.remove('is-drop-target');
      var targetId = row.getAttribute('data-item-id');
      var form = document.getElementById('checklist-reorder');
      if (!form || !draggedId || !targetId || draggedId === targetId) {
        return;
      }
      form.querySelector('[name="itemId"]').value = draggedId;
      form.querySelector('[name="beforeItemId"]').value = targetId;
      form.submit();
    });
  });
})();
