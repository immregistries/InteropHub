import { Editor } from '@tiptap/core'
import ListItem from '@tiptap/extension-list-item'
import StarterKit from '@tiptap/starter-kit'
import { TopicNoteNodeIdExtension } from './topic-note-node-id-extension'

const TopicNoteListItem = ListItem.extend({
  addAttributes() {
    return {
      nodeId: {
        default: null,
        parseHTML: function (element) {
          return element.getAttribute('data-node-id')
        },
        renderHTML: function (attributes) {
          if (!attributes.nodeId) {
            return {}
          }
          return { 'data-node-id': attributes.nodeId }
        }
      }
    }
  }
})

function cloneJson(value) {
  return JSON.parse(JSON.stringify(value))
}

export function buildEmptyBulletDocument() {
  return {
    type: 'doc',
    content: [
      {
        type: 'bulletList',
        content: [
          {
            type: 'listItem',
            attrs: {},
            content: [
              {
                type: 'paragraph'
              }
            ]
          }
        ]
      }
    ]
  }
}

export function createTopicNoteEditor(options) {
  const editor = new Editor({
    element: options.element,
    editable: options.editable,
    extensions: [
      TopicNoteNodeIdExtension,
      StarterKit.configure({
        blockquote: false,
        bold: false,
        bulletList: true,
        code: false,
        codeBlock: false,
        dropcursor: true,
        gapcursor: true,
        hardBreak: false,
        heading: false,
        horizontalRule: false,
        italic: false,
        listItem: false,
        orderedList: false,
        strike: false
      }),
      TopicNoteListItem
    ],
    content: cloneJson(options.content || buildEmptyBulletDocument()),
    onCreate: function () {
      if (typeof options.onCreate === 'function') {
        options.onCreate(editor)
      }
    },
    onUpdate: function () {
      if (typeof options.onUpdate === 'function') {
        options.onUpdate(editor)
      }
    }
  })

  return editor
}