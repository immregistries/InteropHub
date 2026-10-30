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
        var comment = button.getAttribute('data-comment');
        var note = dialog.querySelector('[data-comment-note]');
        if (note && comment != null) {
          note.value = comment;
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

  document.querySelectorAll('[data-series-form]').forEach(function (form) {
    var topic = form.querySelector('[data-topic-select]');
    var filter = function () {
      filterSeries(form);
    };
    if (topic) {
      topic.addEventListener('change', filter);
    }
    filter();
  });

  document.querySelectorAll('[data-roster-filter]').forEach(function (input) {
    var select = document.getElementById(input.getAttribute('data-roster-filter'));
    if (!select) {
      return;
    }
    input.addEventListener('input', function () {
      var query = input.value.trim().toLowerCase();
      Array.prototype.forEach.call(select.options, function (option) {
        if (!option.value) {
          return;
        }
        var show = query.length === 0 || option.text.toLowerCase().indexOf(query) >= 0;
        option.hidden = !show;
        option.disabled = !show;
      });
    });
  });

  document.querySelectorAll('[data-timeline-more]').forEach(function (button) {
    button.addEventListener('click', function () {
      var section = button.closest('section');
      if (!section) {
        return;
      }
      section.querySelectorAll('[data-timeline-older]').forEach(function (row) {
        row.hidden = false;
      });
      button.hidden = true;
    });
  });

  document.querySelectorAll('[data-inline-role]').forEach(function (form) {
    var display = form.querySelector('[data-inline-display]');
    var editor = form.querySelector('input[name="role"]');
    if (!display || !editor) {
      return;
    }
    display.addEventListener('click', function () {
      display.hidden = true;
      editor.hidden = false;
      editor.focus();
      editor.select();
    });
    editor.addEventListener('keydown', function (event) {
      if (event.key === 'Enter') {
        event.preventDefault();
        form.submit();
      } else if (event.key === 'Escape') {
        editor.hidden = true;
        display.hidden = false;
        editor.value = display.textContent === 'Add role' ? '' : display.textContent;
      }
    });
  });

  document.querySelectorAll('[data-copy-value]').forEach(function (button) {
    button.addEventListener('click', function () {
      var text = button.getAttribute('data-copy-value');
      if (!text || !navigator.clipboard || !navigator.clipboard.writeText) {
        return;
      }
      var previous = button.textContent;
      navigator.clipboard.writeText(text).then(function () {
        button.textContent = 'Copied';
        window.setTimeout(function () {
          button.textContent = previous;
        }, 1500);
      });
    });
  });

  function filterSeries(form) {
    var topic = form.querySelector('[data-topic-select]');
    var series = form.querySelector('[data-series-select]');
    var empty = form.querySelector('[data-series-empty]');
    var create = form.querySelector('[data-series-create]');
    if (!topic || !series) {
      return;
    }
    var topicId = topic.value;
    var keep = false;
    Array.prototype.forEach.call(series.options, function (option) {
      if (!option.value) {
        option.hidden = false;
        option.disabled = false;
        return;
      }
      var match = option.getAttribute('data-topic-id') === topicId;
      option.hidden = !match;
      option.disabled = !match;
      if (match && option.value === series.value) {
        keep = true;
      }
    });
    if (!keep) {
      series.value = '';
    }
    var any = false;
    Array.prototype.forEach.call(series.options, function (option) {
      if (option.value && !option.disabled) {
        any = true;
      }
    });
    if (empty) {
      empty.hidden = any;
    }
    if (create) {
      var context = form.getAttribute('data-context') || '';
      create.href = context + '/admin/es/meetings?action=new&topicId=' + encodeURIComponent(topicId);
    }
  }
})();
