import { buildEmptyBulletDocument, createTopicNoteEditor } from './topic-note-editor'

(function () {
  const roots = Array.from(document.querySelectorAll('[data-meeting-note-root]'))
  if (!roots.length) {
    return
  }

  const states = roots.map(setupRoot).filter(Boolean)

  window.addEventListener('beforeunload', function (event) {
    if (!states.some(function (state) { return state.isDirty })) {
      return
    }
    event.preventDefault()
    event.returnValue = ''
  })

  document.addEventListener('click', function (event) {
    const link = event.target.closest('.aira-sidebar-link')
    if (!link) {
      return
    }
    if (!states.some(function (state) { return state.isDirty })) {
      return
    }
    if (!window.confirm('You have unsaved note changes. Leave this agenda item?')) {
      event.preventDefault()
    }
  })

  function setupRoot(root) {
    const configElement = root.querySelector('[data-note-config]')
    if (!configElement) {
      return null
    }

    const config = JSON.parse(configElement.textContent)
    const state = {
      config,
      root,
      readOnlyElement: root.querySelector('[data-note-readonly]'),
      emptyStateElement: root.querySelector('[data-note-empty-state]'),
      ownerTextElement: root.querySelector('[data-note-owner-text]'),
      assumeButton: root.querySelector('[data-note-assume-editorship]'),
      editToggle: root.querySelector('[data-note-edit-toggle]'),
      editPanel: root.querySelector('[data-note-edit]'),
      editorElement: root.querySelector('[data-note-editor]'),
      saveButton: root.querySelector('[data-note-save]'),
      cancelButton: root.querySelector('[data-note-cancel]'),
      dirtyElement: root.querySelector('[data-note-dirty]'),
      saveStateElement: root.querySelector('[data-note-save-state]'),
      messageElement: root.querySelector('[data-note-message]'),
      outcomesRoot: root.querySelector('[data-note-outcomes-root]'),
      outcomesListElement: root.querySelector('[data-outcome-list]'),
      addOutcomeButton: root.querySelector('[data-outcome-add]'),
      readOnlyEditor: null,
      editor: null,
      pollTimer: null,
      noteId: parseNullableNumber(config.noteId),
      revision: Number(config.revision || 0),
      editorVersion: Number(config.editorVersion || 0),
      currentUserId: parseNullableNumber(config.currentUserId),
      activeEditorUserId: parseNullableNumber(config.activeEditorUserId),
      activeEditorDisplayName: trimToNull(config.activeEditorDisplayName),
      noteStatus: trimToNull(config.noteStatus),
      meetingStatus: trimToNull(config.meetingStatus),
      savedAt: trimToNull(config.lastSavedAt),
      canAssumeEditor: Boolean(config.canAssumeEditor),
      canEdit: Boolean(config.canEdit),
      lastSavedDocument: cloneJson(config.initialDocument || buildEmptyBulletDocument()),
      isDirty: false,
      saveInProgress: false,
      saveRequested: false,
      autosaveBlocked: false,
      localConflict: false,
      retryTimer: null,
      retryIndex: 0,
      debounceTimer: null,
      maxTimer: null,
      sse: null,
      sseHealthy: false,
      outcomes: [],
      outcomesBySourceNodeId: {},
      afterNextSuccessfulSave: null,
      pendingCreateAnchorNodeId: null
    }

    renderReadOnly(state, config.readOnlyDocument)
    syncOwnerText(state)
    syncControlVisibility(state)
    setSaveState(state, state.savedAt ? ('Saved at ' + formatTime(state.savedAt)) : 'Saved', 'clean')

    if (state.assumeButton) {
      state.assumeButton.addEventListener('click', function () {
        assumeEditorship(state, true)
      })
    }
    if (state.editToggle) {
      state.editToggle.addEventListener('click', function () {
        enterEditMode(state)
      })
    }
    if (state.saveButton) {
      state.saveButton.addEventListener('click', function () {
        requestSave(state, 'manual', true)
      })
    }
    if (state.cancelButton) {
      state.cancelButton.addEventListener('click', function () {
        closeEditor(state)
      })
    }
    if (state.addOutcomeButton) {
      state.addOutcomeButton.addEventListener('click', function () {
        openCreateOutcomeFlow(state)
      })
    }
    if (state.outcomesListElement) {
      state.outcomesListElement.addEventListener('click', function (event) {
        const editButton = event.target.closest('[data-outcome-edit]')
        if (editButton) {
          editOutcome(state, editButton.getAttribute('data-outcome-edit'))
          return
        }
        const removeButton = event.target.closest('[data-outcome-remove]')
        if (removeButton) {
          removeOutcome(state, removeButton.getAttribute('data-outcome-remove'))
        }
      })
    }

    loadOutcomes(state)

    startRealtime(state)
    startFallbackPollingIfNeeded(state)
    return state
  }

  function startRealtime(state) {
    if (!state.noteId || typeof window.EventSource !== 'function' || !state.config.streamUrl) {
      return
    }

    stopRealtime(state)
    const url = withQueryParam(state.config.streamUrl, 'noteId', state.noteId)
    const source = new EventSource(url)
    state.sse = source

    source.addEventListener('open', function () {
      state.sseHealthy = true
      stopFallbackPolling(state)
    })

    source.addEventListener('error', function () {
      state.sseHealthy = false
      startFallbackPollingIfNeeded(state)
    })

    source.addEventListener('note-state', function (event) {
      handleMetadataEvent(state, parseEventData(event))
      applyOutcomeMarkers(state)
    })

    source.addEventListener('note-updated', function (event) {
      const payload = parseEventData(event)
      if (!payload) {
        return
      }
      const previousRevision = state.revision
      handleMetadataEvent(state, payload)
      if (Number(payload.revision || 0) <= Number(previousRevision)) {
        return
      }
      if (state.isDirty) {
        state.autosaveBlocked = true
        state.localConflict = true
        setSaveState(state, 'Notes changed elsewhere', 'warning')
        setMessage(state, 'Saved notes changed in another browser. Your local unsaved copy is preserved.', 'danger')
        syncControlVisibility(state)
        applyOutcomeMarkers(state)
        return
      }
      refreshSavedContent(state, previousRevision)
      loadOutcomes(state)
    })

    source.addEventListener('editor-changed', function (event) {
      const payload = parseEventData(event)
      if (!payload) {
        return
      }
      const hadEditControl = state.canEdit
      handleMetadataEvent(state, payload)
      if (hadEditControl && !state.canEdit) {
        state.autosaveBlocked = true
        cancelAutosaveTimers(state)
        if (state.editor) {
          setEditorEditable(state, false)
        }
        setSaveState(state, 'Another editor took over', 'warning')
        setMessage(state,
          (state.activeEditorDisplayName || 'Another user')
          + ' is now taking notes. Your local unsaved copy remains visible but was not saved.',
          'danger')
      }
    })

    source.addEventListener('note-state-changed', function (event) {
      const payload = parseEventData(event)
      if (!payload) {
        return
      }
      handleMetadataEvent(state, payload)
      if (state.noteStatus === 'FINALIZED' || state.meetingStatus === 'CLOSED') {
        state.autosaveBlocked = true
        cancelAutosaveTimers(state)
        setSaveState(state, 'Saved', 'clean')
      }
      applyOutcomeMarkers(state)
    })

    source.addEventListener('outcomes-changed', function () {
      loadOutcomes(state)
    })
  }

  function stopRealtime(state) {
    if (state.sse) {
      state.sse.close()
      state.sse = null
    }
  }

  function handleMetadataEvent(state, payload) {
    if (!payload) {
      return
    }

    if (typeof payload.noteId !== 'undefined' && payload.noteId !== null) {
      state.noteId = parseNullableNumber(payload.noteId)
      state.config.noteId = state.noteId
    }
    if (typeof payload.revision !== 'undefined' && payload.revision !== null) {
      state.revision = Number(payload.revision || 0)
      state.config.revision = state.revision
    }
    if (typeof payload.editorVersion !== 'undefined' && payload.editorVersion !== null) {
      state.editorVersion = Number(payload.editorVersion || 0)
      state.config.editorVersion = state.editorVersion
    }
    if (typeof payload.status !== 'undefined' && payload.status !== null) {
      state.noteStatus = trimToNull(payload.status)
      state.config.noteStatus = state.noteStatus
    }
    if (typeof payload.noteStatus !== 'undefined' && payload.noteStatus !== null) {
      state.noteStatus = trimToNull(payload.noteStatus)
      state.config.noteStatus = state.noteStatus
    }
    if (typeof payload.meetingStatus !== 'undefined' && payload.meetingStatus !== null) {
      state.meetingStatus = trimToNull(payload.meetingStatus)
      state.config.meetingStatus = state.meetingStatus
    }
    if (typeof payload.savedAt !== 'undefined' && payload.savedAt !== null) {
      state.savedAt = trimToNull(payload.savedAt)
      if (!state.isDirty) {
        setSaveState(state, state.savedAt ? ('Saved at ' + formatTime(state.savedAt)) : 'Saved', 'clean')
      }
    }

    if (payload.activeEditor && typeof payload.activeEditor.userId !== 'undefined') {
      state.activeEditorUserId = parseNullableNumber(payload.activeEditor.userId)
      state.activeEditorDisplayName = trimToNull(payload.activeEditor.displayName)
    } else if (payload.activeEditor === null) {
      state.activeEditorUserId = null
      state.activeEditorDisplayName = null
    }

    state.canEdit = Boolean(state.currentUserId)
      && Boolean(state.activeEditorUserId)
      && Number(state.currentUserId) === Number(state.activeEditorUserId)
      && state.noteStatus !== 'FINALIZED'
      && state.meetingStatus !== 'CLOSED'

    state.canAssumeEditor = Boolean(state.config.canAssumeEditor)
      && state.noteStatus !== 'FINALIZED'
      && state.meetingStatus !== 'CLOSED'

    syncOwnerText(state)
    syncControlVisibility(state)
  }

  function startFallbackPollingIfNeeded(state) {
    if (state.sseHealthy || state.pollTimer) {
      return
    }
    const intervalMs = Number(state.config.pollIntervalMs || 5000)
    state.pollTimer = window.setInterval(function () {
      if (document.hidden || !state.noteId) {
        return
      }
      if (state.noteStatus === 'FINALIZED' || state.meetingStatus === 'CLOSED') {
        stopFallbackPolling(state)
        return
      }
      pollEditorState(state)
    }, intervalMs)
  }

  function stopFallbackPolling(state) {
    if (!state.pollTimer) {
      return
    }
    window.clearInterval(state.pollTimer)
    state.pollTimer = null
  }

  function assumeEditorship(state, openEditorAfter) {
    if (!state.canAssumeEditor) {
      return
    }

    const url = state.noteId
      ? withQueryParam(state.config.takeOverUrl, 'noteId', state.noteId)
      : state.config.startEditingUrl

    if (!url) {
      return
    }

    setMessage(state, state.noteId ? 'Taking over notes...' : 'Starting note-taking...', 'info')
    disableActionButtons(state, true)

    fetch(url, {
      method: 'POST',
      headers: {
        'X-CSRF-Token': state.config.csrfToken
      }
    })
      .then(readJsonEnvelope)
      .then(function (result) {
        if (!result.response.ok || !result.json.ok) {
          throw new Error(result.json.message || result.json.error || 'Unable to update note editor state.')
        }
        handleMetadataEvent(state, result.json)
        if (result.json.document && !state.isDirty) {
          state.lastSavedDocument = cloneJson(result.json.document)
          renderReadOnly(state, state.lastSavedDocument)
        }
        state.autosaveBlocked = false
        state.localConflict = false
        state.retryIndex = 0
        setMessage(state, state.noteId ? 'You are now taking notes.' : 'Note session started.', 'success')
        setSaveState(state, state.savedAt ? ('Saved at ' + formatTime(state.savedAt)) : 'Saved', 'clean')
        loadOutcomes(state)
        startRealtime(state)
        if (openEditorAfter) {
          enterEditMode(state)
        }
      })
      .catch(function (error) {
        setMessage(state, error.message || 'Unable to update note editor state.', 'danger')
      })
      .finally(function () {
        disableActionButtons(state, false)
      })
  }

  function enterEditMode(state) {
    if (!state.canEdit || !state.editPanel) {
      if (!state.noteId && state.canAssumeEditor) {
        assumeEditorship(state, true)
      }
      return
    }

    const editorContent = cloneJson(state.lastSavedDocument)

    if (!state.editor) {
      state.editor = createTopicNoteEditor({
        element: state.editorElement,
        editable: state.canEdit,
        content: editorContent,
        onUpdate: function (editor) {
          if (!state.canEdit || state.autosaveBlocked) {
            return
          }
          const current = editor.getJSON()
          state.isDirty = JSON.stringify(current) !== JSON.stringify(state.lastSavedDocument)
          toggleDirtyIndicator(state)
          if (state.isDirty) {
            setSaveState(state, 'Unsaved changes', 'dirty')
            scheduleAutosave(state)
          }
          if (state.saveInProgress) {
            state.saveRequested = true
          }
          applyOutcomeMarkers(state)
        }
      })
    } else {
      state.editor.commands.setContent(editorContent, false)
      setEditorEditable(state, state.canEdit)
      applyOutcomeMarkers(state)
    }

    applyOutcomeMarkers(state)

    state.editPanel.hidden = false
    if (state.readOnlyElement) {
      state.readOnlyElement.hidden = true
    }
    if (state.emptyStateElement) {
      state.emptyStateElement.hidden = true
    }
    if (state.editToggle) {
      state.editToggle.hidden = true
    }
    syncControlVisibility(state)
    setMessage(state, '', '')
    state.isDirty = JSON.stringify(editorContent) !== JSON.stringify(state.lastSavedDocument)
    toggleDirtyIndicator(state)
    if (state.canEdit) {
      state.editor.commands.focus('end')
    }
  }

  function closeEditor(state) {
    if (state.isDirty && !window.confirm('Discard unsaved local changes?')) {
      return
    }

    cancelAutosaveTimers(state)
    clearRetry(state)

    if (state.editor) {
      state.editor.commands.setContent(cloneJson(state.lastSavedDocument), false)
      setEditorEditable(state, state.canEdit)
    }

    state.isDirty = false
    toggleDirtyIndicator(state)
    setSaveState(state, state.savedAt ? ('Saved at ' + formatTime(state.savedAt)) : 'Saved', 'clean')
    setMessage(state, '', '')
    exitEditMode(state)
  }

  function exitEditMode(state) {
    if (state.editPanel) {
      state.editPanel.hidden = true
    }
    if (state.readOnlyElement) {
      state.readOnlyElement.hidden = !state.noteId
    }
    if (state.emptyStateElement) {
      state.emptyStateElement.hidden = !!state.noteId
    }
    syncControlVisibility(state)
  }

  function saveNotes(state) {
    if (!state.editor || !state.isDirty) {
      return
    }

    requestSave(state, 'manual', true)
  }

  function scheduleAutosave(state) {
    if (!canAutosave(state)) {
      return
    }
    cancelTimer(state, 'debounceTimer')
    state.debounceTimer = window.setTimeout(function () {
      requestSave(state, 'autosave', false)
    }, Number(state.config.autosaveDebounceMs || 1500))

    if (!state.maxTimer) {
      state.maxTimer = window.setTimeout(function () {
        requestSave(state, 'autosave', true)
      }, Number(state.config.autosaveMaxIntervalMs || 10000))
    }
  }

  function requestSave(state, mode, forceImmediate) {
    if (!canAutosave(state) || !state.editor || !state.isDirty) {
      return
    }

    cancelTimer(state, 'debounceTimer')
    if (forceImmediate) {
      cancelTimer(state, 'maxTimer')
    }

    if (state.saveInProgress) {
      state.saveRequested = true
      return
    }

    executeSave(state, mode)
  }

  function executeSave(state, mode) {
    if (!state.editor) {
      return
    }

    const payload = {
      expectedRevision: state.revision,
      expectedEditorVersion: state.editorVersion,
      document: state.editor.getJSON()
    }

    if (!hasMeaningfulContent(payload.document)) {
      setSaveState(state, 'Unsaved changes', 'dirty')
      return
    }

    state.saveInProgress = true
    setSaveState(state, 'Saving...', 'saving')
    syncControlVisibility(state)

    fetch(state.config.saveUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json;charset=UTF-8',
        'X-CSRF-Token': state.config.csrfToken,
        'X-InteropHub-Save-Mode': mode === 'manual' ? 'manual' : 'autosave'
      },
      body: JSON.stringify(payload)
    })
      .then(readJsonEnvelope)
      .then(function (result) {
        if (!result.response.ok || !result.json.ok) {
          const conflictCode = result.json && result.json.errorCode
          if (result.response.status === 409 && conflictCode) {
            throw new ConflictError(conflictCode, result.json, result.response.status)
          }
          throw new SaveError((result.json && (result.json.message || result.json.error)) || 'Unable to save notes.', result.response.status, result.json)
        }

        state.noteId = parseNullableNumber(result.json.noteId)
        state.revision = Number(result.json.revision || state.revision)
        state.editorVersion = Number(result.json.editorVersion || state.editorVersion)
        if (result.json.activeEditor && typeof result.json.activeEditor.userId !== 'undefined') {
          state.activeEditorUserId = parseNullableNumber(result.json.activeEditor.userId)
          state.activeEditorDisplayName = trimToNull(result.json.activeEditor.displayName)
        }
        state.noteStatus = trimToNull(result.json.status) || state.noteStatus
        state.savedAt = trimToNull(result.json.savedAt) || state.savedAt
        state.config.noteId = state.noteId
        state.config.revision = state.revision
        state.config.editorVersion = state.editorVersion
        state.lastSavedDocument = cloneJson(payload.document)
        state.isDirty = false
        state.retryIndex = 0
        toggleDirtyIndicator(state)
        renderReadOnly(state, state.lastSavedDocument)
        if (state.emptyStateElement) {
          state.emptyStateElement.hidden = true
        }
        syncOwnerText(state)
        syncControlVisibility(state)
        setSaveState(state, 'Saved', 'clean')
        window.setTimeout(function () {
          if (!state.isDirty) {
            setSaveState(state, state.savedAt ? ('Saved at ' + formatTime(state.savedAt)) : 'Saved', 'clean')
          }
        }, 700)
        setMessage(state, '', '')
        loadOutcomes(state)
        if (typeof state.afterNextSuccessfulSave === 'function') {
          const callback = state.afterNextSuccessfulSave
          state.afterNextSuccessfulSave = null
          state.pendingCreateAnchorNodeId = null
          callback()
        }
      })
      .catch(function (error) {
        state.afterNextSuccessfulSave = null
        state.pendingCreateAnchorNodeId = null
        if (error instanceof ConflictError) {
          handleConflict(state, error)
          return
        }
        handleSaveFailure(state, error)
      })
      .finally(function () {
        state.saveInProgress = false
        syncControlVisibility(state)
        if (state.saveRequested && canAutosave(state) && state.isDirty) {
          state.saveRequested = false
          executeSave(state, 'autosave')
        }
      })
  }

  function handleConflict(state, conflictError) {
    clearRetry(state)
    cancelAutosaveTimers(state)
    state.autosaveBlocked = true

    if (conflictError.code === 'NOTE_EDITOR_CHANGED') {
      handleMetadataEvent(state, conflictError.payload)
      setEditorEditable(state, false)
      syncControlVisibility(state)
      syncOwnerText(state)
      setSaveState(state, 'Another editor took over', 'warning')
      setMessage(state,
        buildEditorChangedMessage(state),
        'danger')
      return
    }
    if (conflictError.code === 'NOTE_REVISION_CONFLICT') {
      state.localConflict = true
      setSaveState(state, 'Notes changed elsewhere', 'warning')
      setMessage(state,
        conflictError.payload.message || 'These notes changed after you opened them. Reload the saved notes before saving.',
        'danger')
      return
    }
    setSaveState(state, 'Unable to save', 'danger')
    setMessage(state, conflictError.payload.message || 'Unable to save notes.', 'danger')
  }

  function handleSaveFailure(state, error) {
    const status = error && typeof error.status === 'number' ? error.status : null
    const code = error && error.payload ? error.payload.errorCode : null
    const retryable = !status || status >= 500 || code === 'SERVER_ERROR'

    if (!retryable) {
      state.autosaveBlocked = true
      setSaveState(state, 'Unable to save', 'danger')
      setMessage(state, error.message || 'Unable to save notes.', 'danger')
      return
    }

    const delay = nextRetryDelay(state)
    setSaveState(state, 'Unable to save. Retrying...', 'warning')
    setMessage(state, 'Unable to save right now. Retrying automatically.', 'danger')
    clearRetry(state)
    state.retryTimer = window.setTimeout(function () {
      if (!canAutosave(state) || !state.isDirty) {
        return
      }
      executeSave(state, 'autosave')
    }, delay)
  }

  function buildEditorChangedMessage(state) {
    const editorName = state.activeEditorDisplayName || 'Another user'
    return editorName + ' is now taking notes. Your unsaved changes are still visible in this browser, but they were not saved. You may take over again if you need to continue.'
  }

  function preserveVisibleDraft(state) {
    if (state.editor) {
      const localDraftDocument = cloneJson(state.editor.getJSON())
      state.isDirty = JSON.stringify(localDraftDocument) !== JSON.stringify(state.lastSavedDocument)
      toggleDirtyIndicator(state)
    }
  }

  function pollEditorState(state) {
    const url = withQueryParam(state.config.editorStateUrl, 'noteId', state.noteId)
    fetch(url, { method: 'GET' })
      .then(readJsonEnvelope)
      .then(function (result) {
        if (!result.response.ok || !result.json.ok) {
          return
        }

        const previousEditorVersion = state.editorVersion
        const previousActiveEditor = state.activeEditorUserId
        const previousCanEdit = state.canEdit
        const previousRevision = state.revision

        handleMetadataEvent(state, result.json)

        const editorChanged = previousEditorVersion !== state.editorVersion
          || previousActiveEditor !== state.activeEditorUserId
        if (editorChanged && previousCanEdit && !state.canEdit) {
          state.autosaveBlocked = true
          cancelAutosaveTimers(state)
          setEditorEditable(state, false)
          syncControlVisibility(state)
          syncOwnerText(state)
          setSaveState(state, 'Another editor took over', 'warning')
          setMessage(state, buildEditorChangedMessage(state), 'danger')
          return
        }

        if (editorChanged && !previousCanEdit && state.canEdit) {
          state.autosaveBlocked = false
          setEditorEditable(state, true)
          syncControlVisibility(state)
          syncOwnerText(state)
          setMessage(state, 'You are now taking notes for this topic.', 'success')
        }

        if (!state.isDirty && state.revision > previousRevision) {
          refreshSavedContent(state, previousRevision)
        }
      })
      .catch(function () {
        // Polling is best effort and should not interrupt local editing.
      })
  }

  function refreshSavedContent(state, sinceRevision) {
    if (!state.noteId || !state.config.contentUrl) {
      return
    }
    let url = withQueryParam(state.config.contentUrl, 'noteId', state.noteId)
    const baselineRevision = typeof sinceRevision === 'number'
      ? sinceRevision
      : Math.max(0, Number(state.revision || 0) - 1)
    url = withQueryParam(url, 'sinceRevision', baselineRevision)
    fetch(url, { method: 'GET' })
      .then(function (response) {
        if (response.status === 304) {
          return null
        }
        return response.json().then(function (json) {
          return { response, json }
        })
      })
      .then(function (result) {
        if (!result || !result.response.ok || !result.json || !result.json.ok) {
          return
        }
        handleMetadataEvent(state, {
          revision: result.json.revision,
          editorVersion: result.json.editorVersion,
          status: result.json.status,
          meetingStatus: result.json.meetingStatus,
          activeEditor: result.json.activeEditor,
          savedAt: result.json.savedAt
        })
        if (state.isDirty || !result.json.document) {
          applyOutcomeMarkers(state)
          return
        }
        state.lastSavedDocument = cloneJson(result.json.document)
        renderReadOnly(state, state.lastSavedDocument)
        if (state.editor) {
          state.editor.commands.setContent(cloneJson(state.lastSavedDocument), false)
        }
        applyOutcomeMarkers(state)
      })
      .catch(function () {
        // best effort refresh
      })
  }

  function loadOutcomes(state) {
    if (!state.noteId || !state.config.outcomesUrl) {
      state.outcomes = []
      state.outcomesBySourceNodeId = {}
      renderOutcomes(state)
      applyOutcomeMarkers(state)
      return Promise.resolve()
    }
    const url = withQueryParam(state.config.outcomesUrl, 'noteId', state.noteId)
    return fetch(url, { method: 'GET' })
      .then(readJsonEnvelope)
      .then(function (result) {
        if (!result.response.ok || !result.json.ok) {
          return
        }
        state.outcomes = Array.isArray(result.json.outcomes) ? result.json.outcomes : []
        state.outcomesBySourceNodeId = {}
        for (let i = 0; i < state.outcomes.length; i += 1) {
          const outcome = state.outcomes[i]
          if (outcome && outcome.sourceNodeId) {
            state.outcomesBySourceNodeId[outcome.sourceNodeId] = outcome
          }
        }
        renderOutcomes(state)
        applyOutcomeMarkers(state)
      })
      .catch(function () {
        renderOutcomes(state)
        applyOutcomeMarkers(state)
      })
  }

  function renderOutcomes(state) {
    if (!state.outcomesListElement) {
      return
    }
    if (!state.noteId) {
      state.outcomesListElement.innerHTML = '<div class="aira-empty-state"><p class="aira-empty-state__title">Start or open notes to record outcomes.</p></div>'
      return
    }
    if (!state.outcomes.length) {
      state.outcomesListElement.innerHTML = '<div class="aira-empty-state"><p class="aira-empty-state__title">No recorded outcomes yet.</p></div>'
      return
    }

    let html = ''
    for (let index = 0; index < state.outcomes.length; index += 1) {
      const outcome = state.outcomes[index]
      const typeLabel = humanizeEnum(outcome.outcomeType || 'OUTCOME')
      const title = escapeHtml(outcome.shortTitle || typeLabel)
      const sourceText = escapeHtml(trimToNull(outcome.sourceText) || '(No bullet text)')
      const outcomeText = escapeHtml(outcome.outcomeText || '')
      const sourceNodeId = escapeHtml(outcome.sourceNodeId || '')
      const canManage = Boolean(state.canEdit)
      html += '<article class="mw-outcome-item" data-source-node-id="' + sourceNodeId + '">'
      html += '<div class="aira-cluster aira-cluster--between">'
      html += '<p class="mw-outcome-title"><strong>' + title + '</strong></p>'
      html += '<span class="aira-badge aira-badge--subtle">' + escapeHtml(typeLabel) + '</span>'
      html += '</div>'
      html += '<p class="aira-meta">From bullet: ' + sourceText + '</p>'
      html += '<p>' + outcomeText + '</p>'
      if (canManage) {
        html += '<div class="aira-cluster">'
        html += '<button type="button" class="aira-button aira-button--secondary" data-outcome-edit="' + outcome.outcomeId + '">Edit</button>'
        html += '<button type="button" class="aira-button aira-button--danger" data-outcome-remove="' + outcome.outcomeId + '">Remove</button>'
        html += '</div>'
      }
      html += '</article>'
    }
    state.outcomesListElement.innerHTML = html
  }

  function applyOutcomeMarkers(state) {
    const markerTargets = []
    if (state.readOnlyElement) {
      markerTargets.push(state.readOnlyElement)
    }
    if (state.editorElement) {
      markerTargets.push(state.editorElement)
    }

    for (let i = 0; i < markerTargets.length; i += 1) {
      const target = markerTargets[i]
      const marked = target.querySelectorAll('li.mw-note-outcome-marker')
      for (let m = 0; m < marked.length; m += 1) {
        marked[m].classList.remove('mw-note-outcome-marker')
        marked[m].removeAttribute('data-outcome-type')
        marked[m].removeAttribute('title')
      }

      const candidates = target.querySelectorAll('li[data-node-id]')
      for (let c = 0; c < candidates.length; c += 1) {
        const nodeId = candidates[c].getAttribute('data-node-id')
        if (!nodeId) {
          continue
        }
        const outcome = state.outcomesBySourceNodeId[nodeId]
        if (!outcome) {
          continue
        }
        candidates[c].classList.add('mw-note-outcome-marker')
        candidates[c].setAttribute('data-outcome-type', String(outcome.outcomeType || 'OUTCOME').toLowerCase())
        const hintText = (outcome.shortTitle || humanizeEnum(outcome.outcomeType || 'OUTCOME'))
          + ': '
          + (trimToNull(outcome.outcomeText) || '')
        candidates[c].setAttribute('title', hintText)
      }
    }
  }

  function openCreateOutcomeFlow(state) {
    if (!state.canEdit || !state.editor) {
      return
    }
    const anchor = selectedListItemAnchor(state)
    if (!anchor || !anchor.sourceNodeId) {
      setMessage(state, 'Place your cursor inside a single bullet to record an outcome.', 'danger')
      return
    }
    if (state.outcomesBySourceNodeId[anchor.sourceNodeId]) {
      setMessage(state, 'This bullet already has a recorded outcome. Edit it from the outcomes panel.', 'danger')
      return
    }

    state.pendingCreateAnchorNodeId = anchor.sourceNodeId
    if (state.isDirty) {
      state.afterNextSuccessfulSave = function () {
        openCreateOutcomeDialog(state, state.pendingCreateAnchorNodeId)
      }
      requestSave(state, 'manual', true)
      return
    }
    openCreateOutcomeDialog(state, anchor.sourceNodeId)
  }

  function openCreateOutcomeDialog(state, sourceNodeId) {
    const outcomeType = pickOutcomeType(state, null)
    if (!outcomeType) {
      return
    }
    const shortTitle = window.prompt('Optional short title for this outcome:', '')
    if (shortTitle === null) {
      return
    }
    const outcomeText = window.prompt('Outcome text (required):', '')
    if (outcomeText === null) {
      return
    }
    if (!trimToNull(outcomeText)) {
      setMessage(state, 'Outcome text is required.', 'danger')
      return
    }

    submitOutcomeMutation(state, state.config.createOutcomeUrl, {
      noteId: state.noteId,
      sourceNodeId,
      outcomeType,
      shortTitle,
      outcomeText,
      expectedEditorVersion: state.editorVersion
    }, 'Outcome recorded.')
  }

  function editOutcome(state, outcomeIdRaw) {
    if (!state.canEdit) {
      return
    }
    const outcomeId = parseNullableNumber(outcomeIdRaw)
    if (!outcomeId) {
      return
    }
    const outcome = state.outcomes.find(function (item) {
      return Number(item.outcomeId) === Number(outcomeId)
    })
    if (!outcome) {
      return
    }

    const outcomeType = pickOutcomeType(state, outcome.outcomeType)
    if (!outcomeType) {
      return
    }
    const shortTitle = window.prompt('Short title (optional):', outcome.shortTitle || '')
    if (shortTitle === null) {
      return
    }
    const outcomeText = window.prompt('Outcome text (required):', outcome.outcomeText || '')
    if (outcomeText === null) {
      return
    }
    if (!trimToNull(outcomeText)) {
      setMessage(state, 'Outcome text is required.', 'danger')
      return
    }

    submitOutcomeMutation(state, state.config.updateOutcomeUrl, {
      outcomeId,
      outcomeType,
      shortTitle,
      outcomeText,
      expectedEditorVersion: state.editorVersion
    }, 'Outcome updated.')
  }

  function removeOutcome(state, outcomeIdRaw) {
    if (!state.canEdit) {
      return
    }
    const outcomeId = parseNullableNumber(outcomeIdRaw)
    if (!outcomeId) {
      return
    }
    if (!window.confirm('Remove this recorded outcome?')) {
      return
    }

    submitOutcomeMutation(state, state.config.removeOutcomeUrl, {
      outcomeId,
      expectedEditorVersion: state.editorVersion
    }, 'Outcome removed.')
  }

  function submitOutcomeMutation(state, url, payload, successMessage) {
    if (!url) {
      return
    }
    fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json;charset=UTF-8',
        'X-CSRF-Token': state.config.csrfToken
      },
      body: JSON.stringify(payload)
    })
      .then(readJsonEnvelope)
      .then(function (result) {
        if (!result.response.ok || !result.json.ok) {
          const code = result.json && result.json.errorCode
          if (result.response.status === 409 && code) {
            throw new ConflictError(code, result.json, result.response.status)
          }
          throw new SaveError((result.json && (result.json.message || result.json.error)) || 'Outcome update failed.', result.response.status, result.json)
        }
        if (typeof result.json.editorVersion !== 'undefined' && result.json.editorVersion !== null) {
          state.editorVersion = Number(result.json.editorVersion)
          state.config.editorVersion = state.editorVersion
        }
        loadOutcomes(state)
        setMessage(state, successMessage, 'success')
      })
      .catch(function (error) {
        if (error instanceof ConflictError) {
          handleConflict(state, error)
          loadOutcomes(state)
          return
        }
        setMessage(state, error.message || 'Outcome update failed.', 'danger')
      })
  }

  function selectedListItemAnchor(state) {
    if (!state.editor || !state.editor.state || !state.editor.state.selection) {
      return null
    }
    const $from = state.editor.state.selection.$from
    for (let depth = $from.depth; depth >= 0; depth -= 1) {
      const node = $from.node(depth)
      if (!node || node.type.name !== 'listItem') {
        continue
      }
      const nodeId = node.attrs && node.attrs.nodeId ? String(node.attrs.nodeId) : null
      if (!nodeId) {
        return null
      }
      return {
        sourceNodeId: nodeId,
        sourceText: trimToNull(node.textContent || '')
      }
    }
    return null
  }

  function pickOutcomeType(state, currentValue) {
    const values = Array.isArray(state.config.outcomeTypes) ? state.config.outcomeTypes : []
    if (!values.length) {
      return null
    }
    const optionsText = values.map(function (value, index) {
      return String(index + 1) + '. ' + humanizeEnum(value)
    }).join('\n')
    const defaultIndex = Math.max(0, values.indexOf(currentValue || values[0])) + 1
    const answer = window.prompt('Choose outcome type by number:\n' + optionsText, String(defaultIndex))
    if (answer === null) {
      return null
    }
    const numeric = Number(answer)
    if (!Number.isFinite(numeric) || numeric < 1 || numeric > values.length) {
      setMessage(state, 'Invalid outcome type selection.', 'danger')
      return null
    }
    return values[Math.floor(numeric) - 1]
  }

  function canAutosave(state) {
    return Boolean(state.canEdit)
      && Boolean(state.noteId)
      && !state.autosaveBlocked
      && state.noteStatus !== 'FINALIZED'
      && state.meetingStatus !== 'CLOSED'
  }

  function cancelAutosaveTimers(state) {
    cancelTimer(state, 'debounceTimer')
    cancelTimer(state, 'maxTimer')
  }

  function clearRetry(state) {
    cancelTimer(state, 'retryTimer')
  }

  function cancelTimer(state, key) {
    if (!state[key]) {
      return
    }
    window.clearTimeout(state[key])
    state[key] = null
  }

  function nextRetryDelay(state) {
    const schedule = [2000, 5000, 10000, 30000]
    const index = Math.min(state.retryIndex, schedule.length - 1)
    state.retryIndex = index + 1
    return schedule[index]
  }

  function setSaveState(state, text, tone) {
    if (!state.saveStateElement) {
      return
    }
    state.saveStateElement.textContent = text
    state.saveStateElement.className = 'mw-note-save-state aira-meta'
    if (tone === 'warning') {
      state.saveStateElement.className += ' mw-note-save-state--warning'
    } else if (tone === 'danger') {
      state.saveStateElement.className += ' mw-note-save-state--danger'
    } else if (tone === 'saving') {
      state.saveStateElement.className += ' mw-note-save-state--saving'
    }
  }

  function renderReadOnly(state, documentJson) {
    if (!state.readOnlyElement) {
      return
    }

    if (state.readOnlyEditor) {
      state.readOnlyEditor.destroy()
      state.readOnlyEditor = null
    }

    if (!documentJson) {
      state.readOnlyElement.innerHTML = ''
      state.readOnlyElement.hidden = true
      return
    }

    state.readOnlyElement.hidden = false
    state.readOnlyEditor = createTopicNoteEditor({
      element: state.readOnlyElement,
      editable: false,
      content: cloneJson(documentJson)
    })
    applyOutcomeMarkers(state)
  }

  function syncOwnerText(state) {
    if (!state.ownerTextElement) {
      return
    }

    let message = state.config.responsibilityMessage || ''
    if (state.noteStatus === 'FINALIZED' || state.meetingStatus === 'CLOSED') {
      if (state.activeEditorDisplayName) {
        message = 'Last editor: ' + state.activeEditorDisplayName
      } else {
        message = 'Notes are read-only.'
      }
    } else if (!state.activeEditorUserId) {
      message = 'No one is currently taking notes for this topic.'
    } else if (state.canEdit) {
      message = 'You are taking notes for this topic.'
    } else {
      message = (state.activeEditorDisplayName || ('User #' + state.activeEditorUserId))
        + ' is taking notes for this topic.'
    }

    state.ownerTextElement.textContent = message
  }

  function syncControlVisibility(state) {
    const showAssume = state.canAssumeEditor && !state.canEdit
    if (state.assumeButton) {
      state.assumeButton.hidden = !showAssume
      state.assumeButton.textContent = state.activeEditorUserId ? 'Take over notes' : 'Start taking notes'
      if (state.activeEditorUserId && state.activeEditorDisplayName) {
        state.assumeButton.setAttribute('aria-label', 'Take over notes from ' + state.activeEditorDisplayName)
      } else {
        state.assumeButton.setAttribute('aria-label', state.assumeButton.textContent)
      }
      state.assumeButton.disabled = state.saveInProgress
    }

    if (state.editToggle) {
      state.editToggle.hidden = !state.canEdit || (state.editPanel && !state.editPanel.hidden)
      state.editToggle.textContent = 'Edit notes'
      state.editToggle.disabled = state.saveInProgress
    }

    if (state.saveButton) {
      state.saveButton.disabled = state.saveInProgress || !state.canEdit || !state.isDirty
    }
    if (state.addOutcomeButton) {
      const editorVisible = Boolean(state.editPanel && !state.editPanel.hidden)
      state.addOutcomeButton.hidden = !state.canEdit || !state.editor || !state.noteId || !editorVisible
      state.addOutcomeButton.disabled = state.saveInProgress
    }
  }

  function setEditorEditable(state, editable) {
    if (!state.editor || typeof state.editor.setEditable !== 'function') {
      return
    }
    state.editor.setEditable(Boolean(editable))
  }

  function disableActionButtons(state, disabled) {
    if (state.assumeButton) {
      state.assumeButton.disabled = disabled
    }
    if (state.editToggle) {
      state.editToggle.disabled = disabled
    }
  }

  function setMessage(state, text, tone) {
    if (!state.messageElement) {
      return
    }
    const value = (text || '').trim()
    if (!value) {
      state.messageElement.hidden = true
      state.messageElement.textContent = ''
      state.messageElement.className = 'mw-note-message'
      return
    }

    state.messageElement.hidden = false
    state.messageElement.textContent = value
    state.messageElement.className = 'mw-note-message aira-alert aira-alert--' + normalizeTone(tone)
  }

  function normalizeTone(tone) {
    switch (tone) {
      case 'success':
        return 'success'
      case 'info':
        return 'info'
      default:
        return 'danger'
    }
  }

  function toggleDirtyIndicator(state) {
    if (!state.dirtyElement) {
      return
    }
    state.dirtyElement.hidden = !state.isDirty
  }

  function readJsonEnvelope(response) {
    return response.json().catch(function () {
      return { ok: false, error: 'Invalid response' }
    }).then(function (json) {
      return { response, json }
    })
  }

  function hasMeaningfulContent(documentJson) {
    let meaningful = false
    walkDocumentNodes(documentJson, function (node) {
      if (node && node.type === 'text' && typeof node.text === 'string' && node.text.trim().length > 0) {
        meaningful = true
      }
    })
    return meaningful
  }

  function walkDocumentNodes(node, visit) {
    if (!node || typeof node !== 'object') {
      return
    }
    visit(node)
    if (!Array.isArray(node.content)) {
      return
    }
    for (let i = 0; i < node.content.length; i += 1) {
      walkDocumentNodes(node.content[i], visit)
    }
  }

  function parseEventData(event) {
    if (!event || !event.data) {
      return null
    }
    try {
      return JSON.parse(event.data)
    } catch (_error) {
      return null
    }
  }

  function formatTime(isoValue) {
    const parsed = new Date(isoValue)
    if (Number.isNaN(parsed.getTime())) {
      return isoValue
    }
    return parsed.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
  }

  function withQueryParam(url, name, value) {
    if (!url) {
      return ''
    }
    const separator = url.indexOf('?') >= 0 ? '&' : '?'
    return url + separator + encodeURIComponent(name) + '=' + encodeURIComponent(String(value))
  }

  function cloneJson(value) {
    return JSON.parse(JSON.stringify(value))
  }

  function parseNullableNumber(value) {
    if (value === null || typeof value === 'undefined') {
      return null
    }
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }

  function trimToNull(value) {
    if (typeof value !== 'string') {
      return null
    }
    const trimmed = value.trim()
    return trimmed ? trimmed : null
  }

  function humanizeEnum(value) {
    if (!value) {
      return ''
    }
    return String(value)
      .toLowerCase()
      .split('_')
      .map(function (part) {
        return part.charAt(0).toUpperCase() + part.slice(1)
      })
      .join(' ')
  }

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;')
  }

  class ConflictError extends Error {
    constructor(code, payload, status) {
      super((payload && (payload.message || payload.error)) || 'Conflict')
      this.code = code
      this.payload = payload || {}
      this.status = status
    }
  }

  class SaveError extends Error {
    constructor(message, status, payload) {
      super(message)
      this.status = status
      this.payload = payload || {}
    }
  }
})()
