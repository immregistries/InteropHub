// Patch attachments and notes throughout an open meeting, without reloading the page.
(function () {
  var configEl = document.getElementById('agenda-live-config');
  if (!configEl) {
    return;
  }
  var config;
  try {
    config = JSON.parse(configEl.textContent);
  } catch (e) {
    return;
  }
  if (!config || !config.stateUrl || config.meetingStatus === 'CLOSED' || config.meetingStatus === 'CANCELLED') {
    return;
  }

  var lastHtml = {};
  var timer = null;

  function applyBlock(id, html) {
    html = html || '';
    if (lastHtml[id] === html) {
      return;
    }
    var el = document.getElementById(id);
    if (!el) {
      return;
    }
    el.innerHTML = html;
    lastHtml[id] = html;
  }

  function applyState(data) {
    if (!data) {
      return;
    }
    (data.items || []).forEach(function (item) {
      var row = document.querySelector('tr[data-agenda-item-id="' + item.agendaItemId + '"]');
      if (row) {
        row.classList.toggle('agenda-row-current', data.meetingStatus === 'IN_SESSION' && !!item.isCurrent);
      }
      applyBlock('agenda-attachments-' + item.agendaItemId, item.attachmentsHtml);
      applyBlock('agenda-notes-' + item.agendaItemId, item.notesHtml);
      applyBlock('agenda-outcomes-' + item.agendaItemId, item.outcomesHtml);
    });
    if (data.meetingStatus === 'CLOSED' || data.meetingStatus === 'CANCELLED') {
      if (timer) {
        clearInterval(timer);
        timer = null;
      }
    }
  }

  function poll() {
    fetch(config.stateUrl, { headers: { Accept: 'application/json' }, credentials: 'same-origin' })
      .then(function (res) {
        return res.ok ? res.json() : null;
      })
      .then(function (data) {
        if (data) {
          applyState(data);
        }
      })
      .catch(function () {
        // Transient network/poll failures are silently ignored; the next
        // interval tick will retry.
      });
  }

  poll();
  timer = setInterval(poll, config.pollIntervalMs || 7000);
})();
