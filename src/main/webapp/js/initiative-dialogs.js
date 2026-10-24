(function () {
  'use strict';

  document.querySelectorAll('[data-dialog-open]').forEach(function (button) {
    button.addEventListener('click', function () {
      var dialog = document.getElementById(button.getAttribute('data-dialog-open'));
      if (!dialog || typeof dialog.showModal !== 'function') {
        return;
      }
      var recordId = button.getAttribute('data-record-id');
      if (recordId) {
        var idInput = dialog.querySelector('[data-record-id]');
        if (idInput) {
          idInput.value = recordId;
        }
        var status = button.getAttribute('data-status');
        var statusSelect = dialog.querySelector('[data-status-select]');
        if (statusSelect && status) {
          statusSelect.value = status;
        }
        var note = dialog.querySelector('[data-status-note]');
        if (note) {
          note.value = '';
        }
        var heading = dialog.querySelector('[data-dialog-heading]');
        var title = button.getAttribute('data-dialog-title');
        if (heading && title) {
          heading.textContent = title;
        }
      }
      if (!dialog.open) {
        dialog.showModal();
      }
    });
  });

  document.querySelectorAll('[data-dialog-close]').forEach(function (button) {
    button.addEventListener('click', function () {
      var dialog = document.getElementById(button.getAttribute('data-dialog-close'));
      if (dialog) {
        dialog.close();
      }
    });
  });

  document.querySelectorAll('dialog[data-open-on-load]').forEach(function (dialog) {
    if (typeof dialog.showModal === 'function' && !dialog.open) {
      dialog.showModal();
    }
  });
})();
