package me.mudkip.moememos.ui.page.memoinput

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.skydoves.sandwich.suspendOnSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.mudkip.moememos.MoeMemosFileProvider
import me.mudkip.moememos.data.model.MemoVisibility
import me.mudkip.moememos.data.model.ShareContent
import me.mudkip.moememos.ext.popBackStackIfLifecycleIsResumed
import me.mudkip.moememos.ext.suspendOnErrorMessage
import me.mudkip.moememos.ui.page.common.LocalRootNavController
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import me.mudkip.moememos.util.extractCustomTags
import me.mudkip.moememos.viewmodel.LocalMemos
import me.mudkip.moememos.viewmodel.LocalUserState
import me.mudkip.moememos.viewmodel.MemoInputViewModel

private const val MaxSelectableImages = 100

@Composable
fun MemoInputPage(
    viewModel: MemoInputViewModel = hiltViewModel(),
    memoIdentifier: String? = null,
    shareContent: ShareContent? = null,
    /**
     * 从标签页进来时预填的标签。
     *
     * Memos 的标签就是正文里的 `#xxx`，所以这里直接填成 `#标签 `：
     * 用户如果留着它，新备忘就落在那个标签页；改掉或删掉，就落到改动后实际包含的标签页；
     * 一个标签都不留就回到总列表——这正是需要的语义，不用额外判断。
     */
    initialTag: String? = null
) {
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    val navController = LocalRootNavController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val memosViewModel = LocalMemos.current
    val userStateViewModel = LocalUserState.current
    val currentAccount by userStateViewModel.currentAccount.collectAsStateWithLifecycle()
    val memo = remember { memosViewModel.memos.toList().find { it.identifier == memoIdentifier } }
    // What the editor was loaded from: `memo`, replaced by the database row once it is read (the list
    // copy above is not refreshed while a push or sync runs, so it can be older).
    var baseline by remember { mutableStateOf(memo) }
    val autosaveEnabled by viewModel.autosaveEnabled.collectAsStateWithLifecycle(initialValue = false)
    var autosaveIdentifier by rememberSaveable { mutableStateOf(memo?.identifier) }
    var autosaveDirty by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }
    // 编辑器打开时的初始正文。标签页进来时带上 `#标签 `，光标落在它后面。
    val seedContent = remember(memoIdentifier, initialTag) {
        memo?.content ?: seedContentForTag(initialTag)
    }
    var initialContent by remember { mutableStateOf(seedContent) }
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(seedContent, TextRange(seedContent.length)))
    }
    var visibilityMenuExpanded by remember { mutableStateOf(false) }
    var tagMenuExpanded by remember { mutableStateOf(false) }
    var photoImageUri by remember { mutableStateOf<Uri?>(null) }
    var showExitConfirmation by remember { mutableStateOf(false) }

    val defaultVisibility = userStateViewModel.currentUser?.defaultVisibility ?: MemoVisibility.PRIVATE
    var currentVisibility by remember { mutableStateOf(memo?.visibility ?: defaultVisibility) }

    val validMimeTypePrefixes = remember {
        setOf("text/")
    }

    // An existing memo nothing was changed in. Autosave must not write it back on Send/Back: if the
    // text is older than the database (see `baseline`), writing it would revert newer changes.
    fun isUntouchedExistingMemo(): Boolean {
        val base = baseline ?: return false
        return !autosaveDirty &&
            text.text == base.content &&
            currentVisibility == base.visibility &&
            viewModel.uploadResources.size == base.resources.size
    }

    fun submit() = coroutineScope.launch {
        val tags = extractCustomTags(text.text)

        if (autosaveEnabled && isUntouchedExistingMemo()) {
            exiting = true
            navController.popBackStack()
            return@launch
        }

        if (autosaveEnabled) {
            viewModel.flushAutosave(text.text, currentVisibility, tags.toList(), clearDraftOnCreate = shareContent == null).suspendOnSuccess {
                exiting = true
                memosViewModel.refreshLocalSnapshot()
                navController.popBackStack()
            }.suspendOnErrorMessage { message ->
                snackbarState.showSnackbar(message)
            }
            return@launch
        }

        memo?.let {
            viewModel.editMemo(memo.identifier, text.text, currentVisibility, tags.toList()).suspendOnSuccess {
                memosViewModel.refreshLocalSnapshot()
                navController.popBackStack()
            }.suspendOnErrorMessage { message ->
                snackbarState.showSnackbar(message)
            }
            return@launch
        }

        viewModel.createMemo(text.text, currentVisibility, tags.toList()).suspendOnSuccess {
            text = TextFieldValue("")
            viewModel.updateDraft("")
            memosViewModel.refreshLocalSnapshot()
            navController.popBackStack()
        }.suspendOnErrorMessage { message ->
            snackbarState.showSnackbar(message)
        }
    }

    fun handleExit() {
        if (autosaveEnabled) {
            coroutineScope.launch {
                // Only a row this editor created may be discarded. `memo` is also null when editing a
                // memo the list has not loaded (e.g. after process death); that memo must not be deleted.
                val autosaveRow = viewModel.autosaveIdentifier
                val ownsAutosaveRow = autosaveRow == null || autosaveRow != memoIdentifier
                if (ownsAutosaveRow && text.text.isEmpty() && viewModel.uploadResources.isEmpty()) {
                    viewModel.discardEmptyAutosave()
                } else if (!isUntouchedExistingMemo()) {
                    viewModel.flushAutosave(
                        text.text,
                        currentVisibility,
                        extractCustomTags(text.text).toList(),
                        clearDraftOnCreate = shareContent == null
                    )
                }
                exiting = true
                memosViewModel.refreshLocalSnapshot()
                navController.popBackStackIfLifecycleIsResumed(lifecycleOwner)
            }
            return
        }
        if (text.text != initialContent || viewModel.uploadResources.size != (baseline?.resources?.size ?: 0)) {
            showExitConfirmation = true
        } else {
            navController.popBackStackIfLifecycleIsResumed(lifecycleOwner)
        }
    }

    fun uploadImages(uris: List<Uri>) = coroutineScope.launch {
        uris.take(MaxSelectableImages).forEach { uri ->
            viewModel.upload(uri, autosaveIdentifier ?: memo?.identifier).suspendOnErrorMessage { message ->
                snackbarState.showSnackbar(message)
            }
        }
        delay(300)
        focusRequester.requestFocus()
    }

    fun uploadImage(uri: Uri) {
        uploadImages(listOf(uri))
    }

    val pickImages = rememberLauncherForActivityResult(
        PickMultipleVisualMedia(MaxSelectableImages)
    ) { uris ->
        if (uris.isNotEmpty()) {
            uploadImages(uris)
        }
    }

    val takePhoto = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (success) {
            photoImageUri?.let { uploadImage(it) }
        }
    }

    val pickAttachment = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let {
            coroutineScope.launch {
                viewModel.upload(it, autosaveIdentifier ?: memo?.identifier).suspendOnErrorMessage { message ->
                    snackbarState.showSnackbar(message)
                }
            }
        }
    }

    BackHandler {
        handleExit()
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            MemoInputTopBar(
                isEditMode = memo != null,
                canSubmit = text.text.isNotEmpty() || viewModel.uploadResources.isNotEmpty(),
                onClose = { handleExit() },
                onSubmit = { submit() }
            )
        },
        bottomBar = {
            MemoInputBottomBar(
                currentAccount = currentAccount,
                currentVisibility = currentVisibility,
                showSpaceVisibility = memo?.visibility == MemoVisibility.SPACE,
                visibilityMenuExpanded = visibilityMenuExpanded,
                onVisibilityExpandedChange = { visibilityMenuExpanded = it },
                onVisibilitySelected = { currentVisibility = it },
                tags = memosViewModel.tags.toList(),
                tagMenuExpanded = tagMenuExpanded,
                onTagExpandedChange = { tagMenuExpanded = it },
                onHashTagClick = {
                    text = replaceSelection(text, "#")
                },
                onTagSelected = { tag ->
                    text = replaceSelection(text, "#$tag ")
                },
                onToggleTodoItem = {
                    text = toggleTodoItemInText(text)
                },
                onPickImage = {
                    pickImages.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                },
                onPickAttachment = {
                    pickAttachment.launch(arrayOf("*/*"))
                },
                onTakePhoto = {
                    try {
                        val uri = MoeMemosFileProvider.getImageUri(navController.context)
                        photoImageUri = uri
                        takePhoto.launch(uri)
                    } catch (e: ActivityNotFoundException) {
                        coroutineScope.launch {
                            snackbarState.showSnackbar(e.localizedMessage ?: "Unable to take picture.")
                        }
                    }
                },
                onFormat = { format ->
                    text = applyMarkdownFormatToText(text, format)
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarState)
        }
    ) { innerPadding ->
        MemoInputEditor(
            modifier = Modifier.padding(innerPadding),
            text = text,
            onTextChange = { updated ->
                if (
                    text.text != updated.text &&
                    updated.selection.start == updated.selection.end &&
                    updated.text.length == text.text.length + 1 &&
                    updated.selection.start > 0 &&
                    updated.text[updated.selection.start - 1] == '\n'
                ) {
                    val handled = handleEnterInText(text)
                    if (handled != null) {
                        text = handled
                        return@MemoInputEditor
                    }
                }
                text = updated
            },
            focusRequester = focusRequester,
            validMimeTypePrefixes = validMimeTypePrefixes,
            onDroppedText = { droppedText ->
                text = text.copy(text = text.text + droppedText)
            },
            uploadResources = viewModel.uploadResources.toList(),
            inputViewModel = viewModel
        )
    }

    if (showExitConfirmation) {
        SaveChangesDialog(
            onSave = {
                showExitConfirmation = false
                submit()
            },
            onDiscard = {
                showExitConfirmation = false
                text = TextFieldValue("")
                navController.popBackStackIfLifecycleIsResumed(lifecycleOwner)
            },
            onDismiss = {
                showExitConfirmation = false
            }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.autosaveIdentifier = autosaveIdentifier
        viewModel.uploadResources.clear()
        when {
            memo != null -> {
                // 附件一律以**数据库那一份**为准：`memo` 是列表里的对象，它的 resources
                // 可能是陈旧的（少了刚加进去的几张）。以前只有"用户什么都没改"时才采用
                // 数据库那一份，否则就用陈旧的——而保存时 updateMemo 会把不在列表里的
                // 附件当成「已移除」处理，真机上丢图就是这么来的。
                val fresh = viewModel.loadMemo(memo.identifier)
                viewModel.uploadResources.addAll(fresh?.resources ?: memo.resources)
                initialContent = memo.content
                // 正文与可见性仍只在"没改过"时才采用数据库那一份，避免覆盖正在编辑的内容。
                if (fresh != null && fresh != memo && isUntouchedExistingMemo()) {
                    baseline = fresh
                    initialContent = fresh.content
                    currentVisibility = fresh.visibility
                    text = TextFieldValue(fresh.content, TextRange(fresh.content.length))
                }
            }

            shareContent != null -> {
                text = TextFieldValue(shareContent.text, TextRange(shareContent.text.length))
                for (item in shareContent.images) {
                    uploadImage(item)
                }
            }

            else -> {
                // After process death with an autosaved row, the restored text is newer than the draft
                if (autosaveIdentifier == null) {
                    viewModel.draft.first()?.let {
                        text = TextFieldValue(it, TextRange(it.length))
                    }
                }
            }
        }
        delay(300)
        focusRequester.requestFocus()
    }

    // Restarts per change; a restart cancels a previous run still waiting for the autosave mutex,
    // so at most one write runs and one (the newest) waits.
    LaunchedEffect(autosaveEnabled, text.text, currentVisibility, viewModel.uploadResources.size) {
        if (!autosaveEnabled) {
            return@LaunchedEffect
        }
        autosaveIdentifier = autosaveIdentifier ?: viewModel.autosaveIdentifier
        if (autosaveIdentifier == null && text.text.isEmpty() && viewModel.uploadResources.isEmpty()) {
            return@LaunchedEffect
        }
        if (isUntouchedExistingMemo()) {
            return@LaunchedEffect
        }
        autosaveDirty = true
        viewModel.autosave(
            text.text,
            currentVisibility,
            extractCustomTags(text.text).toList(),
            clearDraftOnCreate = shareContent == null
        ).suspendOnSuccess {
            autosaveIdentifier = data.identifier
        }
    }

    // Leaving the app: rewrite the latest text and push it now. The deferred push only lives as long
    // as the process, and the app lock tears this page down on return without calling handleExit.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && autosaveEnabled && autosaveDirty && !exiting) {
                viewModel.flushAutosaveInBackground(
                    text.text,
                    currentVisibility,
                    extractCustomTags(text.text).toList(),
                    clearDraftOnCreate = shareContent == null
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (autosaveEnabled) {
                // Already flushed by submit/handleExit; a system-initiated dispose leaves needsSync
                // set, so the deferred push or the next sync uploads the row.
                return@onDispose
            }
            if (memo == null && shareContent == null) {
                viewModel.updateDraft(text.text)
            }
        }
    }
}
