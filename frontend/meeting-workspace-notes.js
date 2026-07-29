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
      outcomeDialogElement: root.querySelector('[data-outcome-dialog]'),
      outcomeDialogForm: root.querySelector('[data-outcome-form]'),
      outcomeDialogTitleElement: root.querySelector('[data-outcome-dialog-title]'),
      outcomeDialogModeElement: root.querySelector('[data-outcome-dialog-mode]'),
      outcomeDialogCloseButton: root.querySelector('[data-outcome-dialog-close]'),
      outcomeDialogCancelButton: root.querySelector('[data-outcome-dialog-cancel]'),
      outcomeDialogSaveButton: root.querySelector('[data-outcome-dialog-save]'),
      outcomeDialogStatusElement: root.querySelector('[data-outcome-dialog-status]'),
      outcomeDialogSummaryElement: root.querySelector('[data-outcome-error-summary]'),
      outcomeDialogSourcePreviewElement: root.querySelector('[data-outcome-source-preview]'),
      outcomeDialogTypeElement: root.querySelector('[data-outcome-type]'),
      outcomeDialogShortTitleElement: root.querySelector('[data-outcome-short-title]'),
      outcomeDialogTextElement: root.querySelector('[data-outcome-text]'),
      outcomeDialogRemoveButton: root.querySelector('[data-outcome-remove]'),
      removeOutcomeDialogElement: root.querySelector('[data-remove-outcome-dialog]'),
      removeOutcomeDialogCancelButton: root.querySelector('[data-remove-outcome-cancel]'),
      removeOutcomeDialogConfirmButton: root.querySelector('[data-remove-outcome-confirm]'),
      removeOutcomeDialogMessageElement: root.querySelector('[data-remove-outcome-message]'),
      readOnlyEditor: null,
      editor: null,
      pollTimer: null,
      canEdit: Boolean(config.canEdit),
      canAssumeEditor: Boolean(config.canAssumeEditor),
      noteId: parseNullableNumber(config.noteId),
      noteStatus: config.noteStatus || null,
      meetingStatus: config.meetingStatus || null,
      revision: Number(config.revision || 0),
      editorVersion: Number(config.editorVersion || 0),
      currentUserId: parseNullableNumber(config.currentUserId),
      activeEditorUserId: parseNullableNumber(config.activeEditorUserId),
      activeEditorDisplayName: config.activeEditorDisplayName || '',
      responsibilityMessage: config.responsibilityMessage || '',
      savedAt: trimToNull(config.lastSavedAt),
      isDirty: false,
      saveInProgress: false,
      saveRequested: false,
      autosaveBlocked: false,
      localConflict: false,
      lastSavedDocument: cloneJson(config.initialDocument || buildEmptyBulletDocument()),
      sse: null,
      sseHealthy: false,
      debounceTimer: null,
      maxTimer: null,
      retryTimer: null,
      retryIndex: 0,
      outcomes: [],
      outcomesBySourceNodeId: {},
      outcomeDialogState: null,
      removeOutcomeDialogState: null,
      afterNextSuccessfulSave: null,
      pendingCreateAnchorNodeId: null
    }

    if (state.assumeButton) {
      state.assumeButton.addEventListener('click', function () {
        handleTakeNotesAction(state)
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
    if (state.outcomeDialogForm) {
      state.outcomeDialogForm.addEventListener('submit', function (event) {
        event.preventDefault()
        submitOutcomeDialog(state)
      })
    }
    if (state.outcomeDialogCloseButton) {
      state.outcomeDialogCloseButton.addEventListener('click', function () {
        closeOutcomeDialog(state)
      })
    }
    if (state.outcomeDialogCancelButton) {
      state.outcomeDialogCancelButton.addEventListener('click', function () {
        closeOutcomeDialog(state)
      })
    }
    if (state.outcomeDialogRemoveButton) {
      state.outcomeDialogRemoveButton.addEventListener('click', function () {
        openRemoveOutcomeDialogFromState(state)
      })
    }
    if (state.outcomeDialogElement) {
      state.outcomeDialogElement.addEventListener('cancel', function (event) {
        event.preventDefault()
        if (!isOutcomeDialogSaving(state)) {
          closeOutcomeDialog(state)
        }
      })
    }
    if (state.removeOutcomeDialogElement) {
      state.removeOutcomeDialogElement.addEventListener('cancel', function (event) {
        event.preventDefault()
        closeRemoveOutcomeDialog(state)
      })
    }
    if (state.removeOutcomeDialogCancelButton) {
      state.removeOutcomeDialogCancelButton.addEventListener('click', function () {
        closeRemoveOutcomeDialog(state)
      })
    }
    if (state.removeOutcomeDialogConfirmButton) {
      state.removeOutcomeDialogConfirmButton.addEventListener('click', function () {
        confirmRemoveOutcome(state)
      })
    }
    if (state.outcomesListElement) {
      state.outcomesListElement.addEventListener('click', function (event) {
        const editButton = event.target.closest('[data-outcome-edit]')
        if (editButton) {
          editOutcome(state, editButton.getAttribute('data-outcome-edit'), editButton)
          return
        }
        const removeButton = event.target.closest('[data-outcome-remove]')
        if (removeButton) {
          queueRemoveOutcome(state, removeButton.getAttribute('data-outcome-remove'), removeButton)
        }
      })
    }
    if (state.readOnlyElement) {
      state.readOnlyElement.addEventListener('click', function (event) {
        const marker = event.target.closest('li[data-outcome-marker="true"][data-node-id]')
        if (marker) {
          openOutcomeForSourceNodeId(state, marker.getAttribute('data-node-id'), marker.textContent || '')
        }
      })
    }
    if (state.editorElement) {
      state.editorElement.addEventListener('click', function (event) {
        const marker = event.target.closest('li[data-outcome-marker="true"][data-node-id]')
        if (marker) {
          openOutcomeForSourceNodeId(state, marker.getAttribute('data-node-id'), marker.textContent || '')
        }
      })
    }

    renderReadOnly(state, config.readOnlyDocument)
    syncOwnerText(state)
    syncControlVisibility(state)
    setSaveState(state, state.savedAt ? ('Saved at ' + formatTime(state.savedAt)) : 'Saved', 'clean')

    if (state.canEdit) {
      enterEditMode(state)
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
        setMessage(state, '', 'success')
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

  function handleTakeNotesAction(state) {
    if (!state.canAssumeEditor) {
      return
    }
    if (state.canEdit) {
      enterEditMode(state)
      return
    }
    assumeEditorship(state, true)
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
      state.readOnlyElement.hidden = !state.noteId || state.canEdit
    }
    if (state.emptyStateElement) {
      state.emptyStateElement.hidden = !!state.noteId || state.canEdit
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
          setMessage(state, '', 'success')
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

    let html = '<div class="aira-table-wrap"><table class="aira-table"><thead><tr><th>Type</th><th>Outcome</th>'
    if (Boolean(state.canEdit)) {
      html += '<th class="imw-outcome-table__actions">Actions</th>'
    }
    html += '</tr></thead><tbody>'
    for (let index = 0; index < state.outcomes.length; index += 1) {
      const outcome = state.outcomes[index]
      const typeLabel = humanizeEnum(outcome.outcomeType || 'OUTCOME')
      const title = escapeHtml(outcome.shortTitle || 'Outcome')
      const outcomeText = escapeHtml(outcome.outcomeText || '')
      const sourceNodeId = escapeHtml(outcome.sourceNodeId || '')
      const canManage = Boolean(state.canEdit)
      html += '<tr class="imw-outcome-row" data-source-node-id="' + sourceNodeId + '">'
      html += '<td><span class="aira-badge aira-badge--subtle">' + escapeHtml(typeLabel) + '</span></td>'
      html += '<td><div class="imw-outcome-title">' + title + '</div><div class="imw-outcome-text">' + outcomeText + '</div></td>'
      if (canManage) {
        html += '<td class="imw-outcome-table__actions"><div class="aira-cluster">'
        html += '<button type="button" class="aira-button aira-button--secondary" data-outcome-edit="' + outcome.outcomeId + '">Edit</button>'
        html += '<button type="button" class="aira-button aira-button--danger" data-outcome-remove="' + outcome.outcomeId + '">Remove</button>'
        html += '</div></td>'
      }
      html += '</tr>'
    }
    html += '</tbody></table></div>'
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
      const marked = target.querySelectorAll('li[data-outcome-marker="true"]')
      for (let m = 0; m < marked.length; m += 1) {
        marked[m].classList.remove('imw-note-outcome-marker')
        marked[m].removeAttribute('data-outcome-marker')
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
        candidates[c].classList.add('imw-note-outcome-marker')
        candidates[c].setAttribute('data-outcome-marker', 'true')
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
    const outcome = state.outcomesBySourceNodeId[anchor.sourceNodeId]
    if (outcome) {
      openOutcomeDialog(state, {
        mode: 'edit',
        outcome,
        sourceNodeId: outcome.sourceNodeId,
        sourceText: outcome.sourceText || anchor.sourceText || '',
        openingControl: state.addOutcomeButton || null
      })
      return
    }
    openOutcomeDialogWithSaveGate(state, {
      mode: 'create',
      sourceNodeId: anchor.sourceNodeId,
      sourceText: anchor.sourceText || '',
      openingControl: state.addOutcomeButton || null
    })
  }

  function openOutcomeForSourceNodeId(state, sourceNodeId, sourceText) {
    if (!state.canEdit || !sourceNodeId) {
      return
    }
    const outcome = state.outcomesBySourceNodeId[sourceNodeId]
    if (outcome) {
      openOutcomeDialog(state, {
        mode: 'edit',
        outcome,
        sourceNodeId,
        sourceText: outcome.sourceText || sourceText || '',
        openingControl: state.addOutcomeButton || null
      })
      return
    }
    openOutcomeDialogWithSaveGate(state, {
      mode: 'create',
      sourceNodeId,
      sourceText: trimToNull(sourceText) || '',
      openingControl: state.addOutcomeButton || null
    })
  }

  function openOutcomeDialogWithSaveGate(state, request) {
    if (state.isDirty) {
      state.afterNextSuccessfulSave = function () {
        openOutcomeDialog(state, request)
      }
      requestSave(state, 'manual', true)
      return
    }
    openOutcomeDialog(state, request)
  }

  function openOutcomeDialog(state, request) {
    if (!state.outcomeDialogElement || !state.outcomeDialogForm) {
      return
    }
    const isEdit = request && request.mode === 'edit'
    const outcome = isEdit ? request.outcome : null
    state.outcomeDialogState = {
      mode: isEdit ? 'edit' : 'create',
      outcomeId: outcome ? parseNullableNumber(outcome.outcomeId) : null,
      sourceNodeId: request && request.sourceNodeId ? String(request.sourceNodeId) : null,
      sourceText: trimToNull(request && request.sourceText) || '',
      openingControl: request && request.openingControl ? request.openingControl : null,
      submitInProgress: false,
      removeRequested: false
    }

    if (state.outcomeDialogTitleElement) {
      state.outcomeDialogTitleElement.textContent = isEdit ? 'Edit Recorded Outcome' : 'Add Recorded Outcome'
    }
    if (state.outcomeDialogModeElement) {
      state.outcomeDialogModeElement.textContent = isEdit ? 'Edit an existing outcome' : 'Record a new outcome from the selected bullet'
    }
    if (state.outcomeDialogSaveButton) {
      state.outcomeDialogSaveButton.textContent = isEdit ? 'Save Changes' : 'Save Recorded Outcome'
    }
    if (state.outcomeDialogRemoveButton) {
      state.outcomeDialogRemoveButton.hidden = !isEdit
    }
    if (state.outcomeDialogSourcePreviewElement) {
      state.outcomeDialogSourcePreviewElement.textContent = state.outcomeDialogState.sourceText || '(No bullet text)'
    }
    if (state.outcomeDialogTypeElement) {
      state.outcomeDialogTypeElement.value = outcome && outcome.outcomeType ? String(outcome.outcomeType) : ''
    }
    if (state.outcomeDialogShortTitleElement) {
      state.outcomeDialogShortTitleElement.value = outcome && outcome.shortTitle ? String(outcome.shortTitle) : ''
    }
    if (state.outcomeDialogTextElement) {
      state.outcomeDialogTextElement.value = outcome && outcome.outcomeText ? String(outcome.outcomeText) : (isEdit ? '' : state.outcomeDialogState.sourceText || '')
    }

    clearOutcomeDialogFeedback(state)
    syncOutcomeDialogAvailability(state)
    if (!state.outcomeDialogElement.open) {
      state.outcomeDialogElement.showModal()
    }
    document.body.classList.add('imw-dialog-open')
    window.setTimeout(function () {
      focusOutcomeDialogInitialField(state)
    }, 0)
  }

  function submitOutcomeDialog(state) {
    if (!state.outcomeDialogState || !state.outcomeDialogElement) {
      return
    }
    if (state.outcomeDialogState.submitInProgress) {
      return
    }
    const formData = readOutcomeDialogData(state)
    const validation = validateOutcomeDialog(state, formData)
    if (!validation.valid) {
      return
    }

    state.outcomeDialogState.submitInProgress = true
    clearOutcomeDialogFeedback(state)
    setOutcomeDialogStatus(state, state.isDirty ? 'Saving the meeting notes before recording this outcome…' : 'Saving…', 'saving')
    syncOutcomeDialogAvailability(state)

    const payload = buildOutcomePayload(state, formData)
    if (state.isDirty) {
      state.afterNextSuccessfulSave = function () {
        submitOutcomeRequest(state, payload)
      }
      requestSave(state, 'manual', true)
      return
    }
    submitOutcomeRequest(state, payload)
  }

  function submitOutcomeRequest(state, payload) {
    const url = payload.remove ? state.config.removeOutcomeUrl
      : (payload.outcomeId ? state.config.updateOutcomeUrl : state.config.createOutcomeUrl)
    if (!url) {
      state.outcomeDialogState.submitInProgress = false
      setOutcomeDialogStatus(state, 'The outcome could not be saved. Your entries have been preserved.', 'danger')
      syncOutcomeDialogAvailability(state)
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
          throw new SaveError((result.json && (result.json.message || result.json.error)) || 'The outcome could not be saved. Your entries have been preserved.', result.response.status, result.json)
        }
        if (typeof result.json.editorVersion !== 'undefined' && result.json.editorVersion !== null) {
          state.editorVersion = Number(result.json.editorVersion)
          state.config.editorVersion = state.editorVersion
        }
        loadOutcomes(state)
        if (payload.remove) {
          setMessage(state, 'Outcome removed.', 'success')
          closeRemoveOutcomeDialog(state, true)
        } else {
          setMessage(state, payload.outcomeId ? 'Outcome updated.' : 'Outcome recorded.', 'success')
          closeOutcomeDialog(state, true)
        }
      })
      .catch(function (error) {
        state.outcomeDialogState.submitInProgress = false
        if (error instanceof ConflictError) {
          handleOutcomeConflict(state, error)
          loadOutcomes(state)
          return
        }
        const message = error.message || 'The outcome could not be saved. Your entries have been preserved.'
        setOutcomeDialogError(state, message)
        setOutcomeDialogStatus(state, message, 'danger')
        setMessage(state, message, 'danger')
      })
  }

  function editOutcome(state, outcomeIdRaw, control) {
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
    openOutcomeDialog(state, {
      mode: 'edit',
      outcome,
      sourceNodeId: outcome.sourceNodeId,
      sourceText: outcome.sourceText || '',
      openingControl: control || null
    })
  }

  function queueRemoveOutcome(state, outcomeIdRaw, control) {
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
    state.removeOutcomeDialogState = {
      outcomeId,
      openingControl: control || null
    }
    if (state.removeOutcomeDialogMessageElement) {
      state.removeOutcomeDialogMessageElement.textContent = 'Remove this recorded outcome? The bullet will remain in the meeting notes.'
    }
    if (!state.removeOutcomeDialogElement.open) {
      state.removeOutcomeDialogElement.showModal()
    }
    document.body.classList.add('imw-dialog-open')
    window.setTimeout(function () {
      if (state.removeOutcomeDialogConfirmButton && typeof state.removeOutcomeDialogConfirmButton.focus === 'function') {
        state.removeOutcomeDialogConfirmButton.focus()
      }
    }, 0)
  }

  function openRemoveOutcomeDialogFromState(state) {
    if (!state.outcomeDialogState || !state.outcomeDialogState.outcomeId) {
      return
    }
    queueRemoveOutcome(state, state.outcomeDialogState.outcomeId, state.outcomeDialogRemoveButton)
  }

  function confirmRemoveOutcome(state) {
    if (!state.removeOutcomeDialogState || !state.removeOutcomeDialogState.outcomeId) {
      return
    }
    const outcomeId = state.removeOutcomeDialogState.outcomeId
    const openingControl = state.removeOutcomeDialogState.openingControl
    state.removeOutcomeDialogState = null
    submitOutcomeRequest(state, {
      remove: true,
      outcomeId,
      expectedEditorVersion: state.editorVersion,
      openingControl: openingControl
    })
  }

  function closeOutcomeDialog(state, restoreFocus) {
    const dialogState = state.outcomeDialogState
    if (!state.outcomeDialogElement || !state.outcomeDialogElement.open) {
      return
    }
    state.outcomeDialogState = null
    clearOutcomeDialogFeedback(state)
    state.outcomeDialogElement.close()
    if (!state.removeOutcomeDialogElement || !state.removeOutcomeDialogElement.open) {
      document.body.classList.remove('imw-dialog-open')
    }
    if (restoreFocus !== false && dialogState && dialogState.openingControl && typeof dialogState.openingControl.focus === 'function') {
      dialogState.openingControl.focus()
    }
  }

  function closeRemoveOutcomeDialog(state, restoreFocus) {
    const dialogState = state.removeOutcomeDialogState
    if (!state.removeOutcomeDialogElement || !state.removeOutcomeDialogElement.open) {
      return
    }
    state.removeOutcomeDialogState = null
    state.removeOutcomeDialogElement.close()
    if (!state.outcomeDialogElement || !state.outcomeDialogElement.open) {
      document.body.classList.remove('imw-dialog-open')
    }
    if (restoreFocus !== false && dialogState && dialogState.openingControl && typeof dialogState.openingControl.focus === 'function') {
      dialogState.openingControl.focus()
    }
  }

  function isOutcomeDialogSaving(state) {
    return Boolean(state.outcomeDialogState && state.outcomeDialogState.submitInProgress)
  }

  function readOutcomeDialogData(state) {
    return {
      outcomeId: state.outcomeDialogState && state.outcomeDialogState.mode === 'edit' ? state.outcomeDialogState.outcomeId : null,
      sourceNodeId: state.outcomeDialogState ? state.outcomeDialogState.sourceNodeId : null,
      outcomeType: state.outcomeDialogTypeElement ? trimToNull(state.outcomeDialogTypeElement.value) : null,
      shortTitle: state.outcomeDialogShortTitleElement ? state.outcomeDialogShortTitleElement.value : '',
      outcomeText: state.outcomeDialogTextElement ? state.outcomeDialogTextElement.value : ''
    }
  }

  function buildOutcomePayload(state, formData) {
    const payload = {
      expectedEditorVersion: state.editorVersion,
      outcomeType: formData.outcomeType,
      shortTitle: trimToNull(formData.shortTitle),
      outcomeText: formData.outcomeText
    }
    if (state.outcomeDialogState && state.outcomeDialogState.mode === 'edit') {
      payload.outcomeId = state.outcomeDialogState.outcomeId
    } else {
      payload.noteId = state.noteId
      payload.sourceNodeId = state.outcomeDialogState ? state.outcomeDialogState.sourceNodeId : null
    }
    return payload
  }

  function validateOutcomeDialog(state, formData) {
    const errors = {}
    if (!formData.outcomeType) {
      errors.outcomeType = 'Select an outcome type.'
    }
    if (trimToNull(formData.shortTitle) && String(formData.shortTitle).length > 200) {
      errors.shortTitle = 'Short title must be no more than 200 characters.'
    }
    if (!trimToNull(formData.outcomeText)) {
      errors.outcomeText = 'Enter the outcome that should be recorded.'
    }
    renderOutcomeDialogErrors(state, errors)
    if (Object.keys(errors).length) {
      setOutcomeDialogStatus(state, 'Please fix the highlighted fields.', 'danger')
      focusFirstOutcomeError(state)
      return { valid: false }
    }
    return { valid: true }
  }

  function renderOutcomeDialogErrors(state, errors) {
    setOutcomeFieldError(state, 'outcomeType', errors.outcomeType || '')
    setOutcomeFieldError(state, 'shortTitle', errors.shortTitle || '')
    setOutcomeFieldError(state, 'outcomeText', errors.outcomeText || '')
    if (state.outcomeDialogSummaryElement) {
      const messages = Object.keys(errors).map(function (key) {
        return errors[key]
      }).filter(Boolean)
      state.outcomeDialogSummaryElement.hidden = !messages.length
      state.outcomeDialogSummaryElement.textContent = messages.join(' ')
    }
  }

  function clearOutcomeDialogFeedback(state) {
    renderOutcomeDialogErrors(state, {})
    setOutcomeDialogStatus(state, '', '')
  }

  function setOutcomeFieldError(state, fieldName, message) {
    const propertyName = 'outcomeDialog' + fieldName.charAt(0).toUpperCase() + fieldName.slice(1) + 'Element'
    const element = state[propertyName]
    const errorElement = state.outcomeDialogElement
      ? state.outcomeDialogElement.querySelector('[data-outcome-field-error="' + fieldName + '"]')
      : null
    if (element) {
      if (message) {
        element.setAttribute('aria-invalid', 'true')
      } else {
        element.removeAttribute('aria-invalid')
      }
    }
    if (errorElement) {
      errorElement.hidden = !message
      errorElement.textContent = message || ''
    }
  }

  function focusFirstOutcomeError(state) {
    if (!state.outcomeDialogElement) {
      return
    }
    const invalid = state.outcomeDialogElement.querySelector('[aria-invalid="true"]')
    if (invalid && typeof invalid.focus === 'function') {
      invalid.focus()
    }
  }

  function focusOutcomeDialogInitialField(state) {
    if (!state.outcomeDialogElement || !state.outcomeDialogElement.open) {
      return
    }
    const invalid = state.outcomeDialogElement.querySelector('[aria-invalid="true"]')
    if (invalid && typeof invalid.focus === 'function') {
      invalid.focus()
      return
    }
    if (state.outcomeDialogTypeElement && !state.outcomeDialogTypeElement.disabled) {
      state.outcomeDialogTypeElement.focus()
      return
    }
    if (state.outcomeDialogTextElement && !state.outcomeDialogTextElement.disabled) {
      state.outcomeDialogTextElement.focus()
      return
    }
    if (state.outcomeDialogSaveButton && !state.outcomeDialogSaveButton.disabled) {
      state.outcomeDialogSaveButton.focus()
    }
  }

  function setOutcomeDialogStatus(state, text, tone) {
    if (!state.outcomeDialogStatusElement) {
      return
    }
    const value = trimToNull(text)
    state.outcomeDialogStatusElement.hidden = !value
    state.outcomeDialogStatusElement.textContent = value || ''
    state.outcomeDialogStatusElement.className = 'imw-outcome-dialog__status aira-meta'
    if (tone === 'danger') {
      state.outcomeDialogStatusElement.className += ' imw-outcome-dialog__status--danger'
    } else if (tone === 'saving') {
      state.outcomeDialogStatusElement.className += ' imw-outcome-dialog__status--saving'
    }
  }

  function setOutcomeDialogError(state, message) {
    if (!state.outcomeDialogSummaryElement) {
      return
    }
    const value = trimToNull(message)
    state.outcomeDialogSummaryElement.hidden = !value
    state.outcomeDialogSummaryElement.textContent = value || ''
  }

  function syncOutcomeDialogAvailability(state) {
    const saving = isOutcomeDialogSaving(state) || state.saveInProgress
    if (state.outcomeDialogSaveButton) {
      state.outcomeDialogSaveButton.disabled = saving || !state.canEdit
    }
    if (state.outcomeDialogCancelButton) {
      state.outcomeDialogCancelButton.disabled = saving
    }
    if (state.outcomeDialogCloseButton) {
      state.outcomeDialogCloseButton.disabled = saving
    }
    if (state.outcomeDialogTypeElement) {
      state.outcomeDialogTypeElement.disabled = saving || !state.canEdit
    }
    if (state.outcomeDialogShortTitleElement) {
      state.outcomeDialogShortTitleElement.disabled = saving || !state.canEdit
    }
    if (state.outcomeDialogTextElement) {
      state.outcomeDialogTextElement.disabled = saving || !state.canEdit
    }
    if (state.outcomeDialogRemoveButton) {
      state.outcomeDialogRemoveButton.disabled = saving || !state.canEdit
    }
    if (state.outcomeDialogElement) {
      state.outcomeDialogElement.setAttribute('aria-busy', saving ? 'true' : 'false')
    }
  }

  function handleOutcomeConflict(state, conflictError) {
    if (!state.outcomeDialogState) {
      handleConflict(state, conflictError)
      return
    }
    if (conflictError.code === 'NOTE_EDITOR_CHANGED') {
      handleMetadataEvent(state, conflictError.payload)
      setOutcomeDialogStatus(state, 'You are no longer the active note editor. This outcome was not saved.', 'danger')
      setOutcomeDialogError(state, 'You are no longer the active note editor. This outcome was not saved.')
      setMessage(state, 'You are no longer the active note editor. This outcome was not saved.', 'danger')
      return
    }
    if (conflictError.code === 'NOTE_REVISION_CONFLICT') {
      state.localConflict = true
      setOutcomeDialogStatus(state, 'The meeting notes changed after this form was opened. Resolve the note conflict before saving the outcome.', 'danger')
      setOutcomeDialogError(state, 'The meeting notes changed after this form was opened. Resolve the note conflict before saving the outcome.')
      setMessage(state, conflictError.payload.message || 'The meeting notes changed after this form was opened. Resolve the note conflict before saving the outcome.', 'danger')
      return
    }
    const message = conflictError.payload.message || 'The outcome could not be saved. Your entries have been preserved.'
    setOutcomeDialogStatus(state, message, 'danger')
    setOutcomeDialogError(state, message)
    setMessage(state, message, 'danger')
  }

  function documentContainsListItemNodeId(documentJson, nodeId) {
    if (!documentJson || !nodeId) {
      return false
    }
    let found = false
    walkDocumentNodes(documentJson, function (node) {
      if (found || !node || node.type !== 'listItem' || !node.attrs || !node.attrs.nodeId) {
        return
      }
      if (String(node.attrs.nodeId) === String(nodeId)) {
        found = true
      }
    })
    return found
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
    state.saveStateElement.className = 'imw-note-save-state aira-meta'
    if (tone === 'warning') {
      state.saveStateElement.className += ' imw-note-save-state--warning'
    } else if (tone === 'danger') {
      state.saveStateElement.className += ' imw-note-save-state--danger'
    } else if (tone === 'saving') {
      state.saveStateElement.className += ' imw-note-save-state--saving'
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

    state.readOnlyElement.hidden = Boolean(state.canEdit)
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

    if (state.canEdit) {
      state.ownerTextElement.hidden = true
      state.ownerTextElement.textContent = ''
      return
    }

    state.ownerTextElement.hidden = false

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
    const editorVisible = Boolean(state.editPanel && !state.editPanel.hidden)
    const showTakeNotes = state.canAssumeEditor && !state.canEdit && !editorVisible
    if (state.assumeButton) {
      state.assumeButton.hidden = !showTakeNotes
      state.assumeButton.textContent = 'Take Notes'
      state.assumeButton.setAttribute('aria-label', 'Take notes')
      state.assumeButton.disabled = state.saveInProgress
    }

    if (state.editToggle) {
      state.editToggle.hidden = true
      state.editToggle.disabled = state.saveInProgress
    }

    if (state.readOnlyElement) {
      state.readOnlyElement.hidden = !state.noteId || state.canEdit || editorVisible
    }
    if (state.emptyStateElement) {
      state.emptyStateElement.hidden = !!state.noteId || state.canEdit || editorVisible
    }

    if (state.saveButton) {
      state.saveButton.disabled = state.saveInProgress || !state.canEdit || !state.isDirty
    }
    if (state.addOutcomeButton) {
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
      state.messageElement.className = 'imw-note-message'
      return
    }

    state.messageElement.hidden = false
    state.messageElement.textContent = value
    state.messageElement.className = 'imw-note-message aira-alert aira-alert--' + normalizeTone(tone)
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
