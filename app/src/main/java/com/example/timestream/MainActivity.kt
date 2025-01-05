package com.example.timestream

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.*
import android.text.style.*
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var myEditText: EditText

    private lateinit var btnCheckBox: ImageButton
    private lateinit var btnUnorderedList: ImageButton
    private lateinit var btnOrderedList: ImageButton
    private lateinit var btnBold: ImageButton
    private lateinit var btnItalic: ImageButton
    private lateinit var btnUnderline: ImageButton
    private lateinit var btnStrikethrough: ImageButton
    private lateinit var btnIndentRight: ImageButton
    private lateinit var btnIndentLeft: ImageButton
    private lateinit var btnHighlight: ImageButton

    // NEW: Add Undo/Redo buttons
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton

    // NEW: Create an instance of our UndoRedoManager
    private val undoRedoManager = UndoRedoManager(maxHistorySize = 50)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        myEditText = findViewById(R.id.myEditText)

        btnCheckBox = findViewById(R.id.btnCheckBox)
        btnUnorderedList = findViewById(R.id.btnUnorderedList)
        btnOrderedList = findViewById(R.id.btnOrderedList)
        btnBold = findViewById(R.id.btnBold)
        btnItalic = findViewById(R.id.btnItalic)
        btnUnderline = findViewById(R.id.btnUnderline)
        btnStrikethrough = findViewById(R.id.btnStrikethrough)
        btnIndentRight = findViewById(R.id.btnIndentRight)
        btnIndentLeft = findViewById(R.id.btnIndentLeft)
        btnHighlight = findViewById(R.id.btnHighlight)

        // NEW: Suppose your layout has these IDs
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)

        val prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        val savedHtml = prefs.getString("KEY_SAVED_TEXT", "") ?: ""
        myEditText.setText(htmlToSpanned(savedHtml))

        // Initialize with one snapshot in the stack (so we can undo from the initial state)
        undoRedoManager.captureState(myEditText)

        btnCheckBox.setOnClickListener { toggleCheckBoxForLine() }
        btnUnorderedList.setOnClickListener { toggleBulletForLine() }
        btnOrderedList.setOnClickListener { toggleOrderedForLine() }
        btnBold.setOnClickListener { toggleBold() }
        btnItalic.setOnClickListener { toggleItalic() }
        btnUnderline.setOnClickListener { toggleUnderline() }
        btnStrikethrough.setOnClickListener { toggleStrikethrough() }
        btnIndentRight.setOnClickListener { indentCurrentLine(increase = true) }
        btnIndentLeft.setOnClickListener { indentCurrentLine(increase = false) }
        btnHighlight.setOnClickListener { toggleHighlightForLine() }

        // Hook up undo/redo
        btnUndo.setOnClickListener { undoRedoManager.undo(myEditText) }
        btnRedo.setOnClickListener { undoRedoManager.redo(myEditText) }
    }

    override fun onPause() {
        super.onPause()
        val currentHtml = spannedToHtml(myEditText.text)
        val prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE).edit()
        prefs.putString("KEY_SAVED_TEXT", currentHtml)
        prefs.apply()
    }

    // -------------------------------------------------------------------------
    // A single helper function to preserve selection *and* push new state to undo history.
    // -------------------------------------------------------------------------
    private fun modifyTextPreservingSelection(action: (SpannableStringBuilder) -> Unit) {
        // Instead of capturing the old state here, we want to capture the new state
        // AFTER we do the modification. So let's do:
        val spannable = SpannableStringBuilder(myEditText.text)
        val oldStart = myEditText.selectionStart
        val oldEnd = myEditText.selectionEnd

        // Perform the modification on the Spannable
        action(spannable)

        // Set the modified text back
        myEditText.text = spannable

        // Restore (clamp) the selection
        val newLength = spannable.length
        val clampedStart = oldStart.coerceIn(0, newLength)
        val clampedEnd = oldEnd.coerceIn(0, newLength)
        myEditText.setSelection(clampedStart, clampedEnd)

        // Finally, capture the *new* state in the undo stack
        undoRedoManager.captureState(myEditText)
    }

    // -------------------------------------------------------------------------
    // PARTIAL-SELECTION STYLE TOGGLES
    // -------------------------------------------------------------------------

    private fun toggleBold() {
        toggleStyleSpan(Typeface.BOLD)
    }

    private fun toggleItalic() {
        toggleStyleSpan(Typeface.ITALIC)
    }

    private fun toggleUnderline() {
        toggleSpan(UnderlineSpan::class.java) { UnderlineSpan() }
    }

    private fun toggleStrikethrough() {
        toggleSpan(StrikethroughSpan::class.java) { StrikethroughSpan() }
    }

    private fun toggleStyleSpan(style: Int) {
        val start = myEditText.selectionStart
        val end = myEditText.selectionEnd
        if (start >= end) return // No selection

        modifyTextPreservingSelection { spannable ->
            val existingSpans = spannable.getSpans(start, end, StyleSpan::class.java)
            var isAlreadyStyle = false
            for (span in existingSpans) {
                if (span.style == style) {
                    spannable.removeSpan(span)
                    isAlreadyStyle = true
                }
            }
            if (!isAlreadyStyle) {
                spannable.setSpan(
                    StyleSpan(style),
                    start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    private fun <T> toggleSpan(
        spanClass: Class<T>,
        newSpanFactory: () -> CharacterStyle
    ) {
        val start = myEditText.selectionStart
        val end = myEditText.selectionEnd
        if (start >= end) return

        modifyTextPreservingSelection { spannable ->
            val existingSpans = spannable.getSpans(start, end, spanClass)
            existingSpans.forEach { spannable.removeSpan(it) }

            if (existingSpans.isEmpty()) {
                spannable.setSpan(
                    newSpanFactory(),
                    start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // LINE-BASED TOGGLES
    // -------------------------------------------------------------------------

    private fun toggleCheckBoxForLine() {
        val lineIndex = getLineIndexForCaret()
        modifyTextPreservingSelection { builder ->
            val (startOffset, endOffset) = getLineOffsets(lineIndex, builder.toString())
            var lineText = builder.substring(startOffset, endOffset)
            val trimmed = lineText.trimStart()

            lineText = when {
                trimmed.startsWith("[  ] ") -> lineText.replaceFirst(Regex("""^\s*\[\s\s]\s"""), "[x] ")
                trimmed.startsWith("[x] ") -> lineText.replaceFirst(Regex("""^\s*\[x]\s"""), "")
                else -> "[  ] $trimmed"
            }
            builder.replace(startOffset, endOffset, lineText)
        }
    }

    private fun toggleBulletForLine() {
        val lineIndex = getLineIndexForCaret()
        modifyTextPreservingSelection { builder ->
            val (startOffset, endOffset) = getLineOffsets(lineIndex, builder.toString())
            val original = builder.substring(startOffset, endOffset)
            val trimmed = original.trimStart()

            val newLine = if (trimmed.startsWith("• ")) {
                original.replaceFirst(Regex("""^\s*•\s"""), "")
            } else {
                "• $trimmed"
            }
            builder.replace(startOffset, endOffset, newLine)
        }
    }

    private fun toggleOrderedForLine() {
        val lineIndex = getLineIndexForCaret()
        modifyTextPreservingSelection { builder ->
            val (startOffset, endOffset) = getLineOffsets(lineIndex, builder.toString())
            val original = builder.substring(startOffset, endOffset)
            val trimmed = original.trimStart()

            val newLine = if (Regex("""^\d+\.\s""").containsMatchIn(trimmed)) {
                original.replaceFirst(Regex("""^\s*\d+\.\s"""), "")
            } else {
                "1. $trimmed"
            }
            builder.replace(startOffset, endOffset, newLine)
        }
    }

    private fun indentCurrentLine(increase: Boolean) {
        val lineIndex = getLineIndexForCaret()
        modifyTextPreservingSelection { builder ->
            val (startOffset, endOffset) = getLineOffsets(lineIndex, builder.toString())
            val existingSpans = builder.getSpans(
                startOffset, endOffset,
                LeadingMarginSpan.Standard::class.java
            )
            val currentSpan = existingSpans.firstOrNull()
            var currentMargin = 0
            if (currentSpan != null) {
                currentMargin = currentSpan.getLeadingMargin(true)
                builder.removeSpan(currentSpan)
            }

            val newMargin = if (increase) {
                (currentMargin + 50).coerceAtMost(200)
            } else {
                (currentMargin - 50).coerceAtLeast(0)
            }

            if (newMargin > 0) {
                val span = LeadingMarginSpan.Standard(newMargin, 0)
                builder.setSpan(
                    span,
                    startOffset, endOffset,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    private fun toggleHighlightForLine() {
        val lineIndex = getLineIndexForCaret()
        modifyTextPreservingSelection { builder ->
            val (startOffset, endOffset) = getLineOffsets(lineIndex, builder.toString())
            val existing = builder.getSpans(startOffset, endOffset, BackgroundColorSpan::class.java)
            existing.forEach { builder.removeSpan(it) }

            if (existing.isEmpty()) {
                builder.setSpan(
                    BackgroundColorSpan(Color.YELLOW),
                    startOffset, endOffset,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Line index / offsets
    // -------------------------------------------------------------------------

    private fun getLineIndexForCaret(): Int {
        val caretPos = myEditText.selectionStart
        if (caretPos <= 0) return 0
        val text = myEditText.text.toString()
        return text.substring(0, caretPos).count { it == '\n' }
    }

    private fun getLineOffsets(lineIndex: Int, text: String): Pair<Int, Int> {
        val lines = text.split("\n")
        if (lineIndex < 0 || lineIndex >= lines.size) return 0 to 0

        var startOffset = 0
        for (i in 0 until lineIndex) {
            startOffset += lines[i].length + 1
        }
        val lineLength = lines[lineIndex].length
        val endOffset = startOffset + lineLength
        return startOffset to endOffset
    }

    // -------------------------------------------------------------------------
    // HTML Conversions
    // -------------------------------------------------------------------------
    private fun spannedToHtml(spanned: Spanned): String {
        return Html.toHtml(spanned, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
    }

    private fun htmlToSpanned(html: String): Spanned {
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
    }
}
