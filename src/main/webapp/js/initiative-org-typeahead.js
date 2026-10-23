(function () {
  'use strict';

  document.querySelectorAll('[data-org-typeahead]').forEach(function (field) {
    var input = field.querySelector('[data-org-query]');
    var idInput = field.querySelector('[data-org-id]');
    var popover = field.querySelector('[data-org-popover]');
    var list = field.querySelector('[data-org-list]');
    var addPanel = field.querySelector('[data-org-new]');
    var newName = field.querySelector('[data-org-new-name]');
    var suggestUrl = field.getAttribute('data-suggest-url');
    if (!input || !idInput || !popover || !list || !suggestUrl) {
      return;
    }

    var timer = null;

    function closeList() {
      popover.hidden = true;
      list.innerHTML = '';
    }

    function chooseExisting(match) {
      idInput.value = String(match.id);
      input.value = match.name;
      if (addPanel) {
        addPanel.hidden = true;
      }
      if (newName) {
        newName.value = '';
      }
      var typeSelect = field.querySelector('[data-org-new-type]');
      if (typeSelect) {
        typeSelect.value = '';
      }
      closeList();
    }

    function chooseNew(query) {
      idInput.value = '';
      if (addPanel) {
        addPanel.hidden = false;
      }
      if (newName && !newName.value) {
        newName.value = query;
      }
      closeList();
      if (newName) {
        newName.focus();
      }
    }

    function render(matches, query) {
      list.innerHTML = '';
      matches.forEach(function (match) {
        var button = document.createElement('button');
        button.type = 'button';
        button.className = 'aira-search-option';
        var title = document.createElement('span');
        title.className = 'aira-search-option__title';
        title.textContent = match.name;
        var meta = document.createElement('span');
        meta.className = 'aira-search-option__meta';
        meta.textContent = match.typeLabel || '';
        button.appendChild(title);
        button.appendChild(meta);
        button.addEventListener('click', function () {
          chooseExisting(match);
        });
        list.appendChild(button);
      });
      var add = document.createElement('button');
      add.type = 'button';
      add.className = 'aira-search-option aira-search-option--action';
      add.textContent = '+ Add new organization';
      add.addEventListener('click', function () {
        chooseNew(query);
      });
      list.appendChild(add);
      popover.hidden = false;
    }

    input.addEventListener('input', function () {
      idInput.value = '';
      window.clearTimeout(timer);
      var query = input.value.trim();
      if (!query) {
        closeList();
        return;
      }
      timer = window.setTimeout(function () {
        fetch(suggestUrl + '?q=' + encodeURIComponent(query), { headers: { Accept: 'application/json' } })
          .then(function (response) { return response.json(); })
          .then(function (payload) { render(payload.matches || [], query); })
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
