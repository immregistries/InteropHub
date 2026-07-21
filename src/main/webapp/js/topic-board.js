(function () {
  const shell = document.querySelector('.tb-shell');
  if (!shell) {
    return;
  }

  const boardCode = shell.dataset.boardCode || '';
  const isCurated = shell.dataset.curated === 'true';
  const apiUrl = (window.location.pathname.startsWith('/') ? '' : '/')
    + window.location.pathname.split('/es/board/')[0]
    + '/es/board/action';

  let draggedTopicId = null;

  shell.addEventListener('click', function (event) {
    const addToggle = event.target.closest('.tb-add-toggle');
    if (addToggle) {
      const cell = addToggle.closest('.tb-cell');
      toggleAddPanel(cell);
      return;
    }

    const removeButton = event.target.closest('.tb-remove-btn');
    if (removeButton) {
      const card = removeButton.closest('.tb-card');
      if (!card) {
        return;
      }
      const topicId = card.dataset.topicId;
      removeCuratedTopic(topicId);
      return;
    }

    const searchOption = event.target.closest('.tb-search-option');
    if (searchOption) {
      const panel = searchOption.closest('.tb-add-panel');
      const cell = searchOption.closest('.tb-cell');
      if (!panel || !cell) {
        return;
      }
      const topicId = searchOption.dataset.topicId;
      placeTopicInCell(topicId, cell, panel);
      return;
    }
  });

  shell.addEventListener('input', function (event) {
    const input = event.target.closest('.tb-add-input');
    if (!input) {
      return;
    }
    const panel = input.closest('.tb-add-panel');
    const query = input.value || '';
    searchTopics(panel, query);
  });

  shell.addEventListener('dragstart', function (event) {
    const handle = event.target.closest('.tb-drag-handle');
    if (!handle) {
      return;
    }
    const card = handle.closest('.tb-card');
    if (!card) {
      return;
    }
    draggedTopicId = card.dataset.topicId;
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'move';
      event.dataTransfer.setData('text/plain', draggedTopicId || '');
    }
  });

  shell.addEventListener('dragend', function () {
    draggedTopicId = null;
    clearDropTargets();
  });

  shell.addEventListener('dragover', function (event) {
    const cell = event.target.closest('.tb-cell');
    if (!cell || !draggedTopicId) {
      return;
    }
    event.preventDefault();
    cell.classList.add('tb-drop-target');
  });

  shell.addEventListener('dragleave', function (event) {
    const cell = event.target.closest('.tb-cell');
    if (!cell) {
      return;
    }
    cell.classList.remove('tb-drop-target');
  });

  shell.addEventListener('drop', function (event) {
    const cell = event.target.closest('.tb-cell');
    if (!cell || !draggedTopicId) {
      return;
    }
    event.preventDefault();
    placeTopicInCell(draggedTopicId, cell, null);
    draggedTopicId = null;
    clearDropTargets();
  });

  function clearDropTargets() {
    shell.querySelectorAll('.tb-drop-target').forEach(function (cell) {
      cell.classList.remove('tb-drop-target');
    });
  }

  function toggleAddPanel(cell) {
    if (!cell) {
      return;
    }
    const panel = cell.querySelector('.tb-add-panel');
    if (!panel) {
      return;
    }
    const hidden = panel.hasAttribute('hidden');
    panel.toggleAttribute('hidden');
    if (hidden) {
      const input = panel.querySelector('.tb-add-input');
      if (input) {
        input.focus();
        searchTopics(panel, input.value || '');
      }
    }
  }

  function searchTopics(panel, query) {
    if (!panel) {
      return;
    }
    const resultsList = panel.querySelector('.tb-search-results');
    if (!resultsList) {
      return;
    }

    const body = new URLSearchParams();
    body.set('action', 'search');
    body.set('boardCode', boardCode);
    body.set('q', query || '');
    body.set('max', '25');

    fetch(apiUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
      body: body.toString()
    })
      .then(readJson)
      .then(function (json) {
        if (!json.ok) {
          throw new Error(json.error || 'Search failed');
        }
        renderSearchResults(resultsList, json.topics || []);
      })
      .catch(function () {
        renderSearchResults(resultsList, []);
      });
  }

  function renderSearchResults(resultsList, topics) {
    resultsList.innerHTML = '';
    if (!topics.length) {
      const li = document.createElement('li');
      li.className = 'tb-empty';
      li.textContent = 'No matching topics';
      resultsList.appendChild(li);
      return;
    }

    topics.forEach(function (topic) {
      const li = document.createElement('li');
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'tb-search-option';
      button.dataset.topicId = String(topic.topicId);

      const main = document.createElement('span');
      main.className = 'tb-search-main';
      main.textContent = topic.topicName || '';
      button.appendChild(main);

      const sub = document.createElement('span');
      sub.className = 'tb-search-sub';
      sub.textContent = (topic.currentStage || 'Not assigned') + ' · ' + (topic.currentPath || 'Not assigned');
      button.appendChild(sub);

      li.appendChild(button);
      resultsList.appendChild(li);
    });
  }

  function placeTopicInCell(topicId, cell, panelToClose) {
    if (!topicId || !cell) {
      return;
    }

    const body = new URLSearchParams();
    body.set('action', 'place');
    body.set('boardCode', boardCode);
    body.set('topicId', topicId);
    body.set('stageDefinitionId', cell.dataset.stageId || '');
    body.set('pathDefinitionId', cell.dataset.pathId || '');

    fetch(apiUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
      body: body.toString()
    })
      .then(readJson)
      .then(function (json) {
        if (!json.ok) {
          throw new Error(json.error || 'Place failed');
        }
        if (panelToClose) {
          panelToClose.setAttribute('hidden', 'hidden');
        }
        moveOrInsertCard(json.topic, json.curatedBoard, cell);
      })
      .catch(function () {
        window.location.reload();
      });
  }

  function removeCuratedTopic(topicId) {
    if (!isCurated || !topicId) {
      return;
    }

    const body = new URLSearchParams();
    body.set('action', 'remove');
    body.set('boardCode', boardCode);
    body.set('topicId', topicId);

    fetch(apiUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
      body: body.toString()
    })
      .then(readJson)
      .then(function (json) {
        if (!json.ok) {
          throw new Error(json.error || 'Remove failed');
        }
        removeCardEverywhere(topicId);
      })
      .catch(function () {
        window.location.reload();
      });
  }

  function moveOrInsertCard(topic, curatedBoard, targetCell) {
    removeCardEverywhere(topic.topicId);

    const cardsContainer = targetCell.querySelector('.tb-cards');
    if (!cardsContainer) {
      return;
    }

    const card = buildCard(topic, curatedBoard);
    cardsContainer.appendChild(card);
    sortCards(cardsContainer);
  }

  function removeCardEverywhere(topicId) {
    shell.querySelectorAll('.tb-card[data-topic-id="' + String(topicId) + '"]').forEach(function (card) {
      card.remove();
    });
  }

  function buildCard(topic, curatedBoard) {
    const article = document.createElement('article');
    article.className = 'tb-card';
    article.dataset.topicId = String(topic.topicId);

    const handle = document.createElement('button');
    handle.type = 'button';
    handle.className = 'tb-drag-handle';
    handle.setAttribute('draggable', 'true');
    handle.setAttribute('title', 'Move topic');
    handle.setAttribute('aria-label', 'Move topic');
    handle.innerHTML = '&#x2630;';
    article.appendChild(handle);

    const link = document.createElement('a');
    link.className = 'tb-topic-link';
    link.href = topic.topicUrl;
    link.textContent = topic.topicName || '';
    article.appendChild(link);

    if (curatedBoard) {
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'tb-remove-btn';
      remove.setAttribute('title', 'Remove from board');
      remove.setAttribute('aria-label', 'Remove from board');
      remove.textContent = 'Remove from board';
      article.appendChild(remove);
    }

    return article;
  }

  function sortCards(cardsContainer) {
    const cards = Array.from(cardsContainer.querySelectorAll('.tb-card'));
    cards.sort(function (a, b) {
      const aName = (a.querySelector('.tb-topic-link')?.textContent || '').toLowerCase();
      const bName = (b.querySelector('.tb-topic-link')?.textContent || '').toLowerCase();
      return aName.localeCompare(bName);
    });
    cards.forEach(function (card) {
      cardsContainer.appendChild(card);
    });
  }

  function readJson(response) {
    return response.json().catch(function () {
      return { ok: false, error: 'Invalid response' };
    });
  }
})();
