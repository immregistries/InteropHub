(function () {
  'use strict';

  var WIDGET_SELECTOR = '.aira-people-search';
  var MIN_QUERY_LENGTH = 2;
  var DEBOUNCE_MS = 250;

  var widgets = document.querySelectorAll(WIDGET_SELECTOR);
  if (!widgets.length) {
    return;
  }

  var contextPath = resolveContextPath();

  widgets.forEach(initWidget);

  function initWidget(widget) {
    var meetingId = widget.getAttribute('data-meeting-id');
    var topicId = widget.getAttribute('data-topic-id');
    var windowOpen = widget.getAttribute('data-window-open') === 'true';
    var input = widget.querySelector('[data-role="query"]');
    var status = widget.querySelector('[data-role="status"]');
    var resultsTable = widget.querySelector('[data-role="results"]');
    var tbody = resultsTable ? resultsTable.querySelector('tbody') : null;
    if (!input || !status || !tbody) {
      return;
    }

    var debounceTimer = null;
    var requestSeq = 0;
    var latestHandledSeq = 0;

    input.addEventListener('input', function () {
      var query = input.value.trim();
      window.clearTimeout(debounceTimer);
      if (query.length < MIN_QUERY_LENGTH) {
        clearResults();
        return;
      }
      debounceTimer = window.setTimeout(function () {
        fetchResults(query);
      }, DEBOUNCE_MS);
    });

    function fetchResults(query) {
      var seq = ++requestSeq;
      status.textContent = 'Searching…';
      var url = contextPath + '/es/meeting-attendance/search-people?meetingId=' + encodeURIComponent(meetingId)
          + (topicId ? '&topicId=' + encodeURIComponent(topicId) : '')
          + '&q=' + encodeURIComponent(query);
      fetch(url, { headers: { Accept: 'application/json' } })
        .then(function (response) { return response.json(); })
        .then(function (json) {
          if (seq < latestHandledSeq) {
            return;
          }
          latestHandledSeq = seq;
          if (!json || !json.ok) {
            status.textContent = 'Search is temporarily unavailable.';
            clearRows();
            return;
          }
          renderResults(json.results || []);
        })
        .catch(function () {
          if (seq >= latestHandledSeq) {
            latestHandledSeq = seq;
            status.textContent = 'Search is temporarily unavailable.';
            clearRows();
          }
        });
    }

    function renderResults(results) {
      clearRows();
      if (results.length === 0) {
        status.textContent = 'No matching InteropHub users.';
        return;
      }
      status.textContent = '';
      results.forEach(function (person) {
        tbody.appendChild(buildRow(person));
      });
    }

    function buildRow(person) {
      var tr = document.createElement('tr');

      var nameTd = document.createElement('td');
      var nameText = person.displayName || person.email || ('User #' + person.userId);
      nameText = nameText;
      nameTd.appendChild(document.createTextNode(nameText));
      if (person.expected) {
        var badge = document.createElement('span');
        badge.className = 'aira-badge aira-badge--info';
        badge.style.marginLeft = '6px';
        badge.textContent = 'Expected';
        nameTd.appendChild(badge);
      }
      tr.appendChild(nameTd);

      var orgTd = document.createElement('td');
      orgTd.textContent = person.organization || '';
      tr.appendChild(orgTd);

      var emailTd = document.createElement('td');
      emailTd.textContent = person.email || '';
      tr.appendChild(emailTd);

      var actionTd = document.createElement('td');
      if (person.checkedIn) {
        var doneBadge = document.createElement('span');
        doneBadge.className = 'aira-badge aira-badge--success';
        doneBadge.textContent = 'Checked in';
        actionTd.appendChild(doneBadge);
      } else if (windowOpen) {
        actionTd.appendChild(buildPresentForm(person));
      }
      tr.appendChild(actionTd);

      return tr;
    }

    function buildPresentForm(person) {
      var form = document.createElement('form');
      form.className = 'aira-inline-form';
      form.method = 'post';
      form.action = contextPath + '/es/meeting-attendance';

      appendHidden(form, 'meetingId', meetingId);
      appendHidden(form, 'action', 'addObserved');
      appendHidden(form, 'displayName', person.displayName || person.email || '');
      appendHidden(form, 'firstName', person.firstName || '');
      appendHidden(form, 'lastName', person.lastName || '');
      appendHidden(form, 'organization', person.organization || '');
      appendHidden(form, 'email', person.email || '');
      if (person.userId) {
        appendHidden(form, 'userId', person.userId);
      }

      var button = document.createElement('button');
      button.className = 'aira-button aira-button--primary';
      button.type = 'submit';
      button.textContent = 'Present';
      form.appendChild(button);

      return form;
    }

    function appendHidden(form, name, value) {
      var el = document.createElement('input');
      el.type = 'hidden';
      el.name = name;
      el.value = value;
      form.appendChild(el);
    }

    function clearRows() {
      while (tbody.firstChild) {
        tbody.removeChild(tbody.firstChild);
      }
    }

    function clearResults() {
      clearRows();
      status.textContent = '';
    }
  }

  function resolveContextPath() {
    var scriptEl = document.currentScript;
    if (!scriptEl) {
      var scripts = document.getElementsByTagName('script');
      for (var i = 0; i < scripts.length; i++) {
        if (scripts[i].src && scripts[i].src.indexOf('/js/meeting-attendance-search.js') !== -1) {
          scriptEl = scripts[i];
          break;
        }
      }
    }
    if (!scriptEl) {
      return '';
    }
    var src = scriptEl.getAttribute('src') || '';
    var markerIndex = src.indexOf('/js/meeting-attendance-search.js');
    if (markerIndex < 0) {
      return '';
    }
    try {
      var url = new URL(src, window.location.href);
      var path = url.pathname;
      var pathMarker = path.indexOf('/js/meeting-attendance-search.js');
      return pathMarker >= 0 ? path.substring(0, pathMarker) : '';
    } catch (e) {
      return '';
    }
  }
})();
