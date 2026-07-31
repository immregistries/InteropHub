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

  const addModal = document.getElementById('tb-add-modal');
  const addModalTitle = document.getElementById('tb-add-modal-title');
  const addModalInput = document.getElementById('tb-add-modal-input');
  const addModalResults = document.getElementById('tb-add-modal-results');
  const addModalCancel = document.getElementById('tb-add-modal-cancel');
  const pendingPathId = addModal ? (addModal.dataset.firstPathId || '') : '';

  let draggedTopicId = null;
  let pendingStageId = '';

  shell.addEventListener('click', function (event) {
    const headerAdd = event.target.closest('.tb-header-add');
    if (headerAdd) {
      openAddModal(headerAdd.dataset.stageId || '', headerAdd.dataset.stageName || '');
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
    if (searchOption && addModal && addModal.open) {
      const topicId = searchOption.dataset.topicId;
      placeTopic(topicId, pendingStageId, pendingPathId, function () {
        if (addModal.open) {
          addModal.close();
        }
      });
      return;
    }
  });

  if (addModalInput) {
    addModalInput.addEventListener('input', function () {
      searchTopics(addModalResults, addModalInput.value || '');
    });
    addModalInput.addEventListener('keydown', function (event) {
      if (event.key === 'Enter') {
        event.preventDefault();
      }
    });
  }

  if (addModalCancel && addModal) {
    addModalCancel.addEventListener('click', function () {
      addModal.close();
    });
  }

  function openAddModal(stageId, stageName) {
    if (!addModal) {
      return;
    }
    pendingStageId = stageId || '';
    if (addModalTitle) {
      addModalTitle.textContent = stageName ? ('Add topic to ' + stageName) : 'Add topic';
    }
    if (addModalInput) {
      addModalInput.value = '';
    }
    if (addModalResults) {
      addModalResults.innerHTML = '';
    }
    addModal.showModal();
    if (addModalInput) {
      addModalInput.focus();
    }
    searchTopics(addModalResults, '');
  }

  shell.addEventListener('dragstart', function (event) {
    const card = event.target.closest('.tb-card');
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

  shell.addEventListener('dragenter', function (event) {
    const zone = event.target.closest('.tb-drop-zone');
    if (!zone || !draggedTopicId) {
      return;
    }
    event.preventDefault();
    zone.classList.add(isTrashZone(zone) ? 'tb-drop-target-trash' : 'tb-drop-target');
  });

  shell.addEventListener('dragover', function (event) {
    const zone = event.target.closest('.tb-drop-zone');
    if (!zone || !draggedTopicId) {
      return;
    }
    event.preventDefault();
    zone.classList.add(isTrashZone(zone) ? 'tb-drop-target-trash' : 'tb-drop-target');
  });

  shell.addEventListener('dragleave', function (event) {
    const zone = event.target.closest('.tb-drop-zone');
    if (!zone) {
      return;
    }
    zone.classList.remove('tb-drop-target');
    zone.classList.remove('tb-drop-target-trash');
  });

  shell.addEventListener('drop', function (event) {
    const zone = event.target.closest('.tb-drop-zone');
    if (!zone || !draggedTopicId) {
      return;
    }
    event.preventDefault();
    const topicId = draggedTopicId;
    if (isTrashZone(zone)) {
      clearTopic(topicId);
    } else {
      placeTopic(topicId, zone.dataset.stageId || '', zone.dataset.pathId || '');
    }
    draggedTopicId = null;
    clearDropTargets();
  });

  function isTrashZone(zone) {
    return zone.classList.contains('tb-path--trash');
  }

  function clearDropTargets() {
    shell.querySelectorAll('.tb-drop-target, .tb-drop-target-trash').forEach(function (zone) {
      zone.classList.remove('tb-drop-target');
      zone.classList.remove('tb-drop-target-trash');
    });
  }

  function searchTopics(resultsList, query) {
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
      .catch(function (err) {
        renderSearchError(resultsList, (err && err.message) ? err.message : 'Unable to search topics.');
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
      sub.textContent = (topic.currentStage || 'Not assigned') + ' \u00b7 ' + (topic.currentPath || 'Not assigned');
      button.appendChild(sub);

      li.appendChild(button);
      resultsList.appendChild(li);
    });
  }

  function renderSearchError(resultsList, message) {
    resultsList.innerHTML = '';
    const li = document.createElement('li');
    li.className = 'tb-empty tb-error';
    li.textContent = message;
    resultsList.appendChild(li);
  }

  function findCell(stageId, pathId) {
    const selector = '.tb-cell[data-stage-id="' + cssEscape(stageId) + '"][data-path-id="' + cssEscape(pathId) + '"]';
    return shell.querySelector(selector);
  }

  function cssEscape(value) {
    return String(value == null ? '' : value).replace(/["\\]/g, '\\$&');
  }

  function placeTopic(topicId, stageId, pathId, onSuccess) {
    if (!topicId) {
      return;
    }

    const body = new URLSearchParams();
    body.set('action', 'place');
    body.set('boardCode', boardCode);
    body.set('topicId', topicId);
    body.set('stageDefinitionId', stageId || '');
    body.set('pathDefinitionId', pathId || '');

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
        moveOrInsertCard(json.topic, json.curatedBoard, findCell(stageId || '', pathId || ''));
        if (onSuccess) {
          onSuccess();
        }
      })
      .catch(function (err) {
        window.alert((err && err.message) ? err.message : 'Unable to place topic on the board.');
        window.location.reload();
      });
  }

  function clearTopic(topicId) {
    if (!topicId) {
      return;
    }

    const body = new URLSearchParams();
    body.set('action', 'clear');
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
        moveOrInsertCard(json.topic, json.curatedBoard, findCell('', ''));
      })
      .catch(function (err) {
        window.alert((err && err.message) ? err.message : 'Unable to remove topic from the board.');
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
      .catch(function (err) {
        window.alert((err && err.message) ? err.message : 'Unable to remove topic from the board.');
        window.location.reload();
      });
  }

  function moveOrInsertCard(topic, curatedBoard, targetCell) {
    removeCardEverywhere(topic.topicId);

    if (!targetCell) {
      return;
    }
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
    article.className = 'aira-entity-card tb-card';
    article.setAttribute('draggable', 'true');
    article.dataset.topicId = String(topic.topicId);

    const handle = document.createElement('button');
    handle.type = 'button';
    handle.className = 'aira-entity-card__handle tb-drag-handle';
    handle.setAttribute('draggable', 'true');
    handle.setAttribute('title', 'Move topic');
    handle.setAttribute('aria-label', 'Move topic');
    handle.innerHTML = '&#x2630;';
    article.appendChild(handle);

    const link = document.createElement('a');
    link.className = 'aira-entity-card__title tb-topic-link';
    link.setAttribute('draggable', 'false');
    link.href = topic.topicUrl;
    link.textContent = topic.topicName || '';
    article.appendChild(link);

    if (curatedBoard) {
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'aira-entity-card__action aira-danger-text tb-remove-btn';
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
