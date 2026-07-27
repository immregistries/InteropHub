import { Extension } from '@tiptap/core'
import { Plugin } from '@tiptap/pm/state'

function isUuid(value) {
  return typeof value === 'string'
    && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)
}

function createNodeId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }

  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (char) {
    const randomValue = Math.floor(Math.random() * 16)
    const nextValue = char === 'x' ? randomValue : (randomValue & 0x3) | 0x8
    return nextValue.toString(16)
  })
}

function nextUniqueNodeId(usedIds) {
  let nodeId = createNodeId()
  while (usedIds.has(nodeId)) {
    nodeId = createNodeId()
  }
  return nodeId
}

export const TopicNoteNodeIdExtension = Extension.create({
  name: 'topicNoteNodeId',

  addProseMirrorPlugins() {
    return [
      new Plugin({
        appendTransaction(transactions, oldState, newState) {
          if (!transactions.some(function (transaction) {
            return transaction.docChanged
          })) {
            return null
          }

          const usedIds = new Set()
          let changed = false
          let transaction = newState.tr

          newState.doc.descendants(function (node, position) {
            if (node.type.name !== 'listItem') {
              return true
            }

            const existingId = node.attrs ? node.attrs.nodeId : null
            let nextId = existingId
            if (!isUuid(existingId) || usedIds.has(existingId)) {
              nextId = nextUniqueNodeId(usedIds)
            }

            usedIds.add(nextId)

            if (existingId !== nextId) {
              transaction = transaction.setNodeMarkup(position, undefined, {
                ...node.attrs,
                nodeId: nextId
              }, node.marks)
              changed = true
            }

            return true
          })

          return changed ? transaction : null
        }
      })
    ]
  }
})