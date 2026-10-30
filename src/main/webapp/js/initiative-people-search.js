(function () {
  'use strict';

  document.querySelectorAll('[data-people-search]').forEach(function (field) {
    var input = field.querySelector('[data-people-query]');
    var popover = field.querySelector('[data-people-popover]');
    var list = field.querySelector('[data-people-list]');
    var chosen = field.querySelector('[data-people-chosen]');
    var source = field.querySelector('[data-people-source]');
    var memberId = field.querySelector('[data-people-member]');
    var userId = field.querySelector('[data-people-user]');
    var fresh = field.querySelector('[data-people-new]');
    var suggestUrl = field.getAttribute('data-suggest-url');
    var allowRoster = field.hasAttribute('data-people-allow-roster');
    if (!input || !popover || !list || !suggestUrl) {
      return;
    }

    var timer = null;

    function setHidden(name, value) {
      if (name) {
        name.value = value || '';
      }
    }

    function closeList() {
      popover.hidden = true;
      list.innerHTML = '';
    }

    function showNew(query) {
      setHidden(source, 'NEW');
      setHidden(memberId, '');
      setHidden(userId, '');
      if (fresh) {
        fresh.hidden = false;
      }
      if (chosen) {
        chosen.hidden = true;
        chosen.textContent = '';
      }
      closeList();
      var first = fresh ? fresh.querySelector('[name="firstName"]') : null;
      if (first) {
        first.focus();
      }
    }

    function choose(hit) {
      if (hit.source === 'ROSTER' && !allowRoster) {
        setHidden(source, '');
        setHidden(memberId, '');
        setHidden(userId, '');
        if (chosen) {
          chosen.hidden = false;
          chosen.textContent = hit.name + ' is already on this initiative.';
        }
        if (fresh) {
          fresh.hidden = true;
        }
        closeList();
        return;
      }
      setHidden(source, hit.source);
      setHidden(memberId, hit.memberId == null ? '' : String(hit.memberId));
      setHidden(userId, hit.userId == null ? '' : String(hit.userId));
      var email = field.querySelector('[name="email"]');
      var first = field.querySelector('[name="firstName"]');
      var last = field.querySelector('[name="lastName"]');
      if (email) {
        email.value = hit.email || '';
      }
      if (first) {
        first.value = hit.firstName || '';
      }
      if (last) {
        last.value = hit.lastName || '';
      }
      if (fresh) {
        fresh.hidden = true;
      }
      if (chosen) {
        var bits = [hit.name, hit.email, hit.organization, hit.sourceLabel].filter(function (part) {
          return part;
        });
        chosen.hidden = false;
        chosen.textContent = bits.join(' · ');
      }
      input.value = hit.name || '';
      closeList();
    }

    function option(text, meta, onClick) {
      var button = document.createElement('button');
      button.type = 'button';
      button.className = 'aira-search-option';
      var title = document.createElement('span');
      title.className = 'aira-search-option__title';
      title.textContent = text;
      button.appendChild(title);
      if (meta) {
        var detail = document.createElement('span');
        detail.className = 'aira-search-option__meta';
        detail.textContent = meta;
        button.appendChild(detail);
      }
      button.addEventListener('click', onClick);
      list.appendChild(button);
    }

    function render(results, query) {
      list.innerHTML = '';
      results.forEach(function (hit) {
        var meta = [hit.email, hit.organization, hit.sourceLabel].filter(function (part) {
          return part;
        }).join(' · ');
        option(hit.name || hit.email || 'Person', meta, function () {
          choose(hit);
        });
      });
      option('Add new', 'First name, last name, and email', function () {
        showNew(query);
      });
      popover.hidden = false;
    }

    input.addEventListener('input', function () {
      setHidden(source, '');
      setHidden(memberId, '');
      setHidden(userId, '');
      window.clearTimeout(timer);
      var query = input.value.trim();
      if (query.length < 2) {
        closeList();
        return;
      }
      timer = window.setTimeout(function () {
        fetch(suggestUrl + '?q=' + encodeURIComponent(query), { headers: { Accept: 'application/json' } })
          .then(function (response) { return response.json(); })
          .then(function (payload) { render(payload.results || [], query); })
          .catch(function () { closeList(); });
      }, 200);
    });

    document.addEventListener('click', function (event) {
      if (!field.contains(event.target)) {
        closeList();
      }
    });
  });
})();
