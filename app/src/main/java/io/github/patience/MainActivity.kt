package io.github.patience

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

class MainActivity : ComponentActivity() {
    private val model: NotesModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        setContent {
            val colors = remember { CdsColors() }
            CompositionLocalProvider(LocalCds provides colors,
                LocalTextSelectionColors provides TextSelectionColors(colors.ink, colors.ink.copy(alpha = .20f))) {
                NotebookScreen(model)
            }
        }
    }

    override fun onStop() {
        model.flush()
        super.onStop()
    }
}

@Composable
private fun NotebookScreen(model: NotesModel) {
    val colors = LocalCds.current
    val active = model.notes.firstOrNull { it.id == model.activeId }
    var directory by rememberSaveable { mutableStateOf(false) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var focusEditor by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    fun closeDirectory() {
        model.ensureActive()
        directory = false
    }
    fun openDirectory() {
        focus.clearFocus()
        keyboard?.hide()
        focusEditor = false
        directory = true
    }
    BackHandler(enabled = directory && deletingId == null) { closeDirectory() }
    Column(Modifier.fillMaxSize().background(colors.canvas).safeDrawingPadding().imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.widthIn(max = 768.dp).fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (directory) {
                val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                CdsButton("返回主页", Modifier.graphicsLayer { if (rtl) scaleX = -1f }, Glyph.Back, iconOnly = true) { closeDirectory() }
            }
            CdsText(if (directory) "会话" else "Patience",
                Modifier.weight(1f).padding(start = if (directory) 8.dp else 16.dp)
                    .semantics { heading() }, size = 20, weight = FontWeight.Medium)
            if (model.ready) {
                if (directory) {
                    CdsButton("新建会话") {
                        model.create()
                        focusEditor = true
                        directory = false
                    }
                } else {
                    CdsButton("会话目录", iconOnly = true, artwork = R.drawable.ui_sessions, onClick = ::openDirectory)
                }
            }
        }
        model.error?.let { message ->
            Row(Modifier.widthIn(max = 768.dp).fillMaxWidth().padding(horizontal = 32.dp)
                .semantics { liveRegion = LiveRegionMode.Assertive }, verticalAlignment = Alignment.CenterVertically) {
                CdsText(message, Modifier.weight(1f))
                CdsButton("重试", onClick = model::retry)
            }
        }
        Box(Modifier.weight(1f).widthIn(max = 768.dp).fillMaxWidth()) {
            when {
                !model.ready -> Unit
                directory -> LazyColumn(Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(16.dp)) {
                    items(model.notes, key = { it.id }) { note ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .testTag("session-${note.id}")
                            .clip(RoundedCornerShape(999.dp)).background(colors.selected)
                            .padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CdsText(note.title, Modifier.weight(1f).padding(end = 8.dp), maxLines = 1)
                            CdsButton("编辑会话：${note.title}", iconOnly = true, artwork = R.drawable.ui_edit) {
                                model.select(note.id)
                                focusEditor = true
                                directory = false
                            }
                            CdsButton("删除会话：${note.title}", iconOnly = true, artwork = R.drawable.ui_delete) {
                                deletingId = note.id
                            }
                        }
                    }
                }
                active != null -> Editor(active, focusEditor, onEdit = { model.edit(active.id, it) })
            }
        }
    }
    deletingId?.let { id ->
        DeleteDialog(onDismiss = { deletingId = null }, onDelete = {
            deletingId = null
            model.delete(id)
        })
    }
}

@Composable
private fun Editor(note: Note, focusOnOpen: Boolean, onEdit: (String) -> Unit) {
    val colors = LocalCds.current
    var value by rememberSaveable(note.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(note.text, TextRange(note.text.length)))
    }
    val focus = remember(note.id) { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(note.id, focusOnOpen) {
        if (note.text.isEmpty() || focusOnOpen) {
            focus.requestFocus()
            keyboard?.show()
        }
    }
    BasicTextField(value = value, onValueChange = {
        value = it
        onEdit(it.text)
    }, modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp)
        .focusRequester(focus).semantics { contentDescription = "记录正文" },
        textStyle = TextStyle(color = colors.ink, fontFamily = FontFamily.SansSerif,
            fontSize = 16.sp, lineHeight = 24.sp),
        cursorBrush = SolidColor(colors.ink),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default))
}

@Composable
private fun DeleteDialog(onDismiss: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalCds.current
    var appeared by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (appeared) 1f else 0f, tween(200, easing = CdsEaseOut))
    LaunchedEffect(Unit) { appeared = true }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(16.dp).widthIn(max = 400.dp).fillMaxWidth()
            .graphicsLayer { alpha = progress; scaleX = .95f + .05f * progress; scaleY = scaleX }
            .clip(RoundedCornerShape(16.dp)).background(colors.panel)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(24.dp).verticalScroll(rememberScrollState())) {
            CdsText("删除这个会话？", Modifier.semantics { heading() }, weight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            CdsText("删除后无法恢复。", secondary = true)
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                CdsButton("取消", onClick = onDismiss)
                CdsButton("删除", filled = true, onClick = onDelete)
            }
        }
    }
}
