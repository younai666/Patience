package io.github.patience

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NotebookTest {
    @get:Rule val ui = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun launch() {
        context.deleteDatabase("notes.db") // This app's disposable emulator data only.
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun close() { scenario.close() }

    private fun flush() {
        ui.waitForIdle()
        scenario.onActivity { ViewModelProvider(it)[NotesModel::class.java].flush() }
    }

    private fun read() = NotesDatabase(context).use { it.read() }

    private fun openDirectory() = ui.onNodeWithContentDescription("会话目录").performClick()

    private fun newSession() {
        openDirectory()
        ui.onNodeWithContentDescription("新建会话").performClick()
    }

    @Test fun createEditAutosaveRecreateAndDelete() {
        val first = "第一条记录\n中文、English 与 emoji 🌱\n\n保留空行及尾随空格  "
        ui.onNodeWithContentDescription("记录正文").performTextInput(first)
        flush()
        assertEquals(first, read().notes.single().text)

        // Keep the process alive but destroy/recreate the entire Activity and its editor.
        scenario.recreate()
        ui.onNodeWithContentDescription("记录正文").assertTextEquals(first)
        val edited = "$first\n再写一行。"
        ui.onNodeWithContentDescription("记录正文").performTextReplacement(edited)
        newSession()
        ui.onNodeWithContentDescription("记录正文").performTextInput("第二条记录\n独立保存")
        flush()
        assertEquals(setOf(edited, "第二条记录\n独立保存"), read().notes.map { it.text }.toSet())

        // A new Activity gets a new model and must recover entirely from disk.
        scenario.close()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        ui.onNodeWithContentDescription("记录正文").assertTextEquals("第二条记录\n独立保存")
        openDirectory()
        ui.onNodeWithContentDescription("编辑会话：第一条记录").performClick()
        ui.onNodeWithContentDescription("记录正文").assertTextEquals(edited)
        flush()
        val activeBeforeDelete = read().activeId
        openDirectory()
        ui.onNodeWithContentDescription("删除会话：第二条记录").performClick()
        ui.onNodeWithContentDescription("取消").performClick()
        ui.onNodeWithContentDescription("返回主页").performClick()
        ui.onNodeWithContentDescription("记录正文").assertTextEquals(edited)
        openDirectory()
        ui.onNodeWithContentDescription("删除会话：第二条记录").performClick()
        ui.onNodeWithContentDescription("删除", substring = false).performClick()
        flush()
        assertEquals(activeBeforeDelete, read().activeId)
        ui.onNodeWithContentDescription("返回主页").performClick()
        ui.onNodeWithContentDescription("记录正文").assertTextEquals(edited)
        flush()
        assertEquals(edited, read().notes.single().text)
    }

    @Test fun emptyTextAndRapidEditsRemainExact() {
        val editor = ui.onNodeWithContentDescription("记录正文")
        repeat(30) { editor.performTextReplacement("快速输入 $it\n换行\n") }
        editor.performTextReplacement("")
        flush()
        assertEquals("", read().notes.single().text)
        scenario.recreate()
        ui.onNodeWithContentDescription("记录正文").assertTextEquals("")
        openDirectory()
        ui.onNodeWithContentDescription("删除会话：新会话").performClick()
        ui.onNodeWithContentDescription("删除", substring = false).performClick()
        flush()
        assertEquals(emptyList<Note>(), read().notes)
        assertNull(read().activeId)
        ui.onNodeWithContentDescription("返回主页").performClick()
        ui.onNodeWithContentDescription("记录正文").assertTextEquals("")
        flush()
        assertEquals(1, read().notes.size)
    }

    @Test fun longPlainTextIsNeverTruncated() {
        val text = (1..1500).joinToString("\n") { "第 $it 行：原样保存 plain text <>& **不是富文本** 🌿" }
        ui.onNodeWithContentDescription("记录正文").performTextInput(text)
        flush()
        assertEquals(text, read().notes.single().text)
        scenario.recreate()
        ui.onNodeWithContentDescription("记录正文").assertTextEquals(text)
    }

    @Test fun capsuleKeepsTitleOnOneLineAndActionsOnTheRight() {
        val title = "很长的会话标题".repeat(30)
        ui.onNodeWithContentDescription("记录正文").performTextInput(title)
        flush()
        val id = read().notes.single().id
        openDirectory()
        ui.onNodeWithContentDescription("记录正文").assertDoesNotExist()
        val row = ui.onNodeWithTag("session-$id").fetchSemanticsNode().boundsInRoot
        val edit = ui.onNodeWithContentDescription("编辑会话：$title").fetchSemanticsNode().boundsInRoot
        val delete = ui.onNodeWithContentDescription("删除会话：$title").fetchSemanticsNode().boundsInRoot
        assertTrue(edit.right <= delete.left)
        assertTrue(delete.right <= row.right)
        assertTrue(edit.top >= row.top && delete.bottom <= row.bottom)
        val layouts = mutableListOf<TextLayoutResult>()
        ui.onNodeWithText(title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        ui.onNodeWithContentDescription("编辑会话：$title").performClick()
        ui.onNodeWithContentDescription("记录正文").assertTextEquals(title)
    }

    @Test fun legacyRecordsOpenDirectlyAndDeletingActiveSelectsRemainingSession() {
        scenario.close()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        context.deleteDatabase("notes.db")
        NotesDatabase(context).use {
            it.save(Note("legacy-first", "原有会话\n不能丢失", 1))
            it.save(Note("legacy-second", "最近会话\n旧版停留在列表", 2))
            it.select(null)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        ui.onNodeWithContentDescription("记录正文").assertTextEquals("最近会话\n旧版停留在列表")
        openDirectory()
        ui.onNodeWithContentDescription("删除会话：最近会话").performClick()
        ui.onNodeWithContentDescription("删除", substring = false).performClick()
        ui.onNodeWithContentDescription("返回主页").performClick()
        ui.onNodeWithContentDescription("记录正文").assertTextEquals("原有会话\n不能丢失")
        flush()
        assertEquals("legacy-first", read().activeId)
        assertEquals("legacy-first", read().notes.single().id)
    }
}
