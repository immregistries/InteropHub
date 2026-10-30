(function () {
  'use strict';

  document.querySelectorAll('[data-dialog-open]').forEach(function (button) {
    button.addEventListener('click', function () {
      var dialog = document.getElementById(button.getAttribute('data-dialog-open'));
      if (!dialog || typeof dialog.showModal !== 'function') {
        return;
      }
      var tab = button.getAttribute('data-add-tab');
      if (tab) {
        selectTab(dialog, tab);
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

  document.querySelectorAll('[data-add-tab]').forEach(function (tab) {
    tab.addEventListener('click', function (event) {
      event.preventDefault();
      var dialog = tab.closest('dialog');
      if (dialog) {
        selectTab(dialog, tab.getAttribute('data-add-tab'));
      }
    });
  });

  document.querySelectorAll('[data-narrative-edit]').forEach(function (button) {
    button.addEventListener('click', function () {
      var section = button.closest('[data-narrative-section]');
      var form = button.closest('[data-narrative-form]');
      if (!section || !form) {
        return;
      }
      section.classList.add('is-editing');
      form.classList.add('is-editing');
      var area = section.querySelector('textarea');
      if (area) {
        area.focus();
      }
    });
  });

  document.querySelectorAll('[data-narrative-cancel]').forEach(function (button) {
    button.addEventListener('click', function () {
      var form = button.closest('[data-narrative-form]');
      if (!form) {
        return;
      }
      form.classList.remove('is-editing');
      form.querySelectorAll('[data-narrative-section]').forEach(function (section) {
        section.classList.remove('is-editing');
        var area = section.querySelector('textarea');
        if (area && area.hasAttribute('data-original')) {
          area.value = area.getAttribute('data-original');
        }
      });
    });
  });

  var submitting = false;
  document.addEventListener('submit', function () {
    submitting = true;
  });
  window.addEventListener('beforeunload', function (event) {
    if (submitting || !dirty()) {
      return;
    }
    event.preventDefault();
    event.returnValue = '';
  });

  function selectTab(dialog, tab) {
    dialog.querySelectorAll('[data-add-tab]').forEach(function (button) {
      button.setAttribute('aria-selected', button.getAttribute('data-add-tab') === tab ? 'true' : 'false');
    });
    dialog.querySelectorAll('[data-add-panel]').forEach(function (panel) {
      panel.classList.toggle('is-selected', panel.getAttribute('data-add-panel') === tab);
    });
  }

  function dirty() {
    var areas = document.querySelectorAll('textarea[data-watch]');
    for (var i = 0; i < areas.length; i++) {
      var original = areas[i].getAttribute('data-original');
      if (original == null) {
        original = '';
      }
      if (areas[i].value !== original) {
        return true;
      }
    }
    return false;
  }
})();
