(function () {
  'use strict';

  var SEARCH_INPUT_ID = 'aira-global-search-input';
  var MIN_QUERY_LENGTH = 2;
  var DEBOUNCE_MS = 250;

  var input = document.getElementById(SEARCH_INPUT_ID);
  if (!input) {
    return;
  }
  var form = input.closest('form');
  var wrapper = input.closest('.aira-global-search');
  if (!form || !wrapper) {
    return;
  }

  var contextPath = resolveContextPath();
  var suggestUrl = contextPath + '/search/suggest';

  var popover = buildPopover();
  wrapper.appendChild(popover);
  var listbox = popover.querySelector('#global-search-listbox');
  var viewAll = popover.querySelector('.aira-search-option--action');

  input.setAttribute('role', 'combobox');
  input.setAttribute('aria-expanded', 'false');
  input.setAttribute('aria-controls', 'global-search-listbox');
  input.setAttribute('aria-autocomplete', 'list');
  input.setAttribute('autocomplete', 'off');

  var debounceTimer = null;
  var requestSeq = 0;
  var latestHandledSeq = 0;
  var navigableItems = []; // flat list of <a>/<li> elements the user can arrow through
  var activeIndex = -1;

  input.addEventListener('input', function () {
    var query = input.value.trim();
    window.clearTimeout(debounceTimer);
    if (query.length < MIN_QUERY_LENGTH) {
      closePopover();
      return;
    }
    debounceTimer = window.setTimeout(function () {
      fetchSuggestions(query);
    }, DEBOUNCE_MS);
  });

  input.addEventListener('keydown', function (event) {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      if (!isOpen()) {
        var query = input.value.trim();
        if (query.length >= MIN_QUERY_LENGTH) {
          fetchSuggestions(query);
        }
        return;
      }
      moveActive(1);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      moveActive(-1);
    } else if (event.key === 'Enter') {
      if (activeIndex >= 0 && navigableItems[activeIndex]) {
        event.preventDefault();
        window.location.href = navigableItems[activeIndex].href;
      }
      // Otherwise let the native form submission open the full results page.
    } else if (event.key === 'Escape') {
      if (isOpen()) {
        event.preventDefault();
        closePopover();
      }
    }
  });

  document.addEventListener('click', function (event) {
    if (!wrapper.contains(event.target)) {
      closePopover();
    }
  });

  function fetchSuggestions(query) {
    var seq = ++requestSeq;
    renderLoading();
    openPopover();

    var url = suggestUrl + '?q=' + encodeURIComponent(query);
    fetch(url, { headers: { Accept: 'application/json' } })
      .then(readJson)
      .then(function (json) {
        if (seq < latestHandledSeq) {
          return; // A newer request already rendered; drop this stale one.
        }
        latestHandledSeq = seq;
        if (!json || !json.ok) {
          renderError();
          return;
        }
        renderResults(json, query);
      })
      .catch(function () {
        if (seq >= latestHandledSeq) {
          latestHandledSeq = seq;
          renderError();
        }
      });
  }

  function renderLoading() {
    listbox.innerHTML = '';
    var status = document.createElement('p');
    status.className = 'aira-search-status';
    status.textContent = 'Searching…';
    listbox.appendChild(status);
    viewAll.hidden = true;
    navigableItems = [];
    activeIndex = -1;
    input.removeAttribute('aria-activedescendant');
  }

  function renderError() {
    listbox.innerHTML = '';
    var status = document.createElement('p');
    status.className = 'aira-search-status';
    status.textContent = 'Search is temporarily unavailable. Please try again.';
    listbox.appendChild(status);
    viewAll.hidden = true;
    navigableItems = [];
    activeIndex = -1;
    input.removeAttribute('aria-activedescendant');
  }

  function renderResults(json, query) {
    listbox.innerHTML = '';
    navigableItems = [];
    activeIndex = -1;
    input.removeAttribute('aria-activedescendant');

    var optionSeq = 0;
    appendGroup('Topics', json.topics, function (item) {
      return {
        title: item.titleHtml,
        meta: [item.spaceName, item.stage].filter(Boolean).join(' · '),
        matchHtml: item.summaryHtml,
        href: item.url
      };
    });
    appendGroup('Upcoming meetings', json.upcomingMeetings, function (item) {
      return {
        title: item.titleHtml,
        meta: [item.whenText, item.spaceName].filter(Boolean).join(' · '),
        matchHtml: item.matchLabel
            ? '<strong>' + escapeHtml(item.matchLabel) + ':</strong> ' + item.matchDetailHtml
            : null,
        href: item.url
      };
    });
    appendGroup('Previous meetings', json.previousMeetings, function (item) {
      return {
        title: item.titleHtml,
        meta: [item.whenText, item.spaceName].filter(Boolean).join(' · '),
        matchHtml: item.matchLabel
            ? '<strong>' + escapeHtml(item.matchLabel) + ':</strong> ' + item.matchDetailHtml
            : null,
        href: item.url
      };
    });

    var hasAnyResults = navigableItems.length > 0;
    if (!hasAnyResults) {
      var status = document.createElement('p');
      status.className = 'aira-search-status';
      status.textContent = 'No matching topics or meetings';
      listbox.appendChild(status);
    }

    viewAll.href = contextPath + '/search?q=' + encodeURIComponent(query);
    viewAll.textContent = 'View all results for “' + query + '”';
    viewAll.hidden = false;
    navigableItems.push(viewAll);

    function appendGroup(label, items, mapItem) {
      if (!items || items.length === 0) {
        return;
      }
      var group = document.createElement('div');
      group.className = 'aira-search-group';
      group.setAttribute('role', 'group');
      group.setAttribute('aria-label', label);

      var groupLabel = document.createElement('p');
      groupLabel.className = 'aira-search-group__label';
      groupLabel.textContent = label;
      group.appendChild(groupLabel);

      items.forEach(function (rawItem) {
        var mapped = mapItem(rawItem);
        var option = document.createElement('a');
        option.className = 'aira-search-option';
        option.setAttribute('role', 'option');
        option.id = 'global-search-option-' + (optionSeq++);
        option.href = mapped.href;

        var title = document.createElement('span');
        title.className = 'aira-search-option__title';
        title.innerHTML = mapped.title;
        option.appendChild(title);

        if (mapped.meta) {
          var meta = document.createElement('span');
          meta.className = 'aira-search-option__meta';
          meta.textContent = mapped.meta;
          option.appendChild(meta);
        }

        if (mapped.matchHtml) {
          var match = document.createElement('span');
          match.className = 'aira-search-option__match';
          match.innerHTML = mapped.matchHtml;
          option.appendChild(match);
        }

        group.appendChild(option);
        navigableItems.push(option);
      });

      listbox.appendChild(group);
    }
  }

  function moveActive(delta) {
    if (navigableItems.length === 0) {
      return;
    }
    var nextIndex = activeIndex + delta;
    if (nextIndex < 0) {
      nextIndex = 0;
    }
    if (nextIndex > navigableItems.length - 1) {
      nextIndex = navigableItems.length - 1;
    }
    setActive(nextIndex);
  }

  function setActive(index) {
    if (activeIndex >= 0 && navigableItems[activeIndex]) {
      navigableItems[activeIndex].removeAttribute('aria-selected');
    }
    activeIndex = index;
    var el = navigableItems[activeIndex];
    if (el) {
      el.setAttribute('aria-selected', 'true');
      input.setAttribute('aria-activedescendant', el.id);
      el.scrollIntoView({ block: 'nearest' });
    }
  }

  function openPopover() {
    popover.hidden = false;
    input.setAttribute('aria-expanded', 'true');
  }

  function closePopover() {
    popover.hidden = true;
    input.setAttribute('aria-expanded', 'false');
    input.removeAttribute('aria-activedescendant');
    navigableItems = [];
    activeIndex = -1;
  }

  function isOpen() {
    return !popover.hidden;
  }

  function buildPopover() {
    var el = document.createElement('div');
    el.className = 'aira-search-popover';
    el.id = 'global-search-popover';
    el.hidden = true;

    var box = document.createElement('div');
    box.className = 'aira-search-listbox';
    box.id = 'global-search-listbox';
    box.setAttribute('role', 'listbox');
    el.appendChild(box);

    var viewAllLink = document.createElement('a');
    viewAllLink.className = 'aira-search-option aira-search-option--action';
    viewAllLink.setAttribute('role', 'option');
    viewAllLink.id = 'global-search-option-view-all';
    viewAllLink.hidden = true;
    el.appendChild(viewAllLink);

    return el;
  }

  function escapeHtml(value) {
    if (!value) {
      return '';
    }
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function readJson(response) {
    return response.json().catch(function () {
      return { ok: false };
    });
  }

  function resolveContextPath() {
    var scriptEl = document.currentScript;
    if (!scriptEl) {
      var scripts = document.getElementsByTagName('script');
      for (var i = 0; i < scripts.length; i++) {
        if (scripts[i].src && scripts[i].src.indexOf('/js/header-search.js') !== -1) {
          scriptEl = scripts[i];
          break;
        }
      }
    }
    if (!scriptEl) {
      return '';
    }
    var src = scriptEl.getAttribute('src') || '';
    var markerIndex = src.indexOf('/js/header-search.js');
    if (markerIndex < 0) {
      return '';
    }
    try {
      var url = new URL(src, window.location.href);
      var path = url.pathname;
      var pathMarker = path.indexOf('/js/header-search.js');
      return pathMarker >= 0 ? path.substring(0, pathMarker) : '';
    } catch (e) {
      return '';
    }
  }
})();
