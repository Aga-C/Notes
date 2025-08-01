package org.fossify.notes.activities

import android.accounts.NetworkErrorException
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_ACTIVITY_NO_HISTORY
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.text.method.ArrowKeyMovementMethod
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.ActionMode
import android.view.Gravity
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.net.toUri
import androidx.viewpager.widget.ViewPager
import org.fossify.commons.dialogs.ConfirmationAdvancedDialog
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.dialogs.SecurityDialog
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.checkWhatsNew
import org.fossify.commons.extensions.clearBackgroundSpans
import org.fossify.commons.extensions.convertToBitmap
import org.fossify.commons.extensions.deleteFile
import org.fossify.commons.extensions.fadeIn
import org.fossify.commons.extensions.fadeOut
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getCurrentFormattedDateTime
import org.fossify.commons.extensions.getDocumentFile
import org.fossify.commons.extensions.getFilenameFromContentUri
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperStatusBarColor
import org.fossify.commons.extensions.getRealPathFromURI
import org.fossify.commons.extensions.handleDeletePasswordProtection
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.highlightText
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.extensions.launchMoreAppsFromUsIntent
import org.fossify.commons.extensions.needsStupidWritePermissions
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.onPageChangeListener
import org.fossify.commons.extensions.onTextChangeListener
import org.fossify.commons.extensions.performSecurityCheck
import org.fossify.commons.extensions.searchMatches
import org.fossify.commons.extensions.shortcutManager
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.value
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.LICENSE_RTL
import org.fossify.commons.helpers.PERMISSION_READ_STORAGE
import org.fossify.commons.helpers.PERMISSION_WRITE_STORAGE
import org.fossify.commons.helpers.PROTECTION_NONE
import org.fossify.commons.helpers.REAL_FILE_PATH
import org.fossify.commons.helpers.SHOW_ALL_TABS
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isQPlus
import org.fossify.commons.models.FAQItem
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.Release
import org.fossify.commons.views.MyEditText
import org.fossify.notes.BuildConfig
import org.fossify.notes.R
import org.fossify.notes.adapters.NotesPagerAdapter
import org.fossify.notes.databases.NotesDatabase
import org.fossify.notes.databinding.ActivityMainBinding
import org.fossify.notes.dialogs.DeleteNoteDialog
import org.fossify.notes.dialogs.ExportFileDialog
import org.fossify.notes.dialogs.ImportFolderDialog
import org.fossify.notes.dialogs.NewNoteDialog
import org.fossify.notes.dialogs.OpenFileDialog
import org.fossify.notes.dialogs.OpenNoteDialog
import org.fossify.notes.dialogs.RenameNoteDialog
import org.fossify.notes.dialogs.SortChecklistDialog
import org.fossify.notes.extensions.config
import org.fossify.notes.extensions.getPercentageFontSize
import org.fossify.notes.extensions.notesDB
import org.fossify.notes.extensions.parseChecklistItems
import org.fossify.notes.extensions.updateWidgets
import org.fossify.notes.extensions.widgetsDB
import org.fossify.notes.fragments.TextFragment
import org.fossify.notes.helpers.MIME_TEXT_PLAIN
import org.fossify.notes.helpers.MyMovementMethod
import org.fossify.notes.helpers.NEW_CHECKLIST
import org.fossify.notes.helpers.NEW_TEXT_NOTE
import org.fossify.notes.helpers.NotesHelper
import org.fossify.notes.helpers.OPEN_NOTE_ID
import org.fossify.notes.helpers.SHORTCUT_NEW_CHECKLIST
import org.fossify.notes.helpers.SHORTCUT_NEW_TEXT_NOTE
import org.fossify.notes.models.Note
import org.fossify.notes.models.NoteType
import java.io.File
import java.nio.charset.Charset

class MainActivity : SimpleActivity() {
    private val EXPORT_FILE_SYNC = 1
    private val EXPORT_FILE_NO_SYNC = 2

    private val IMPORT_FILE_SYNC = 1
    private val IMPORT_FILE_NO_SYNC = 2

    private val PICK_OPEN_FILE_INTENT = 1
    private val PICK_EXPORT_FILE_INTENT = 2

    private lateinit var mCurrentNote: Note
    private var mNotes = listOf<Note>()
    private var mAdapter: NotesPagerAdapter? = null
    private var noteViewWithTextSelected: MyEditText? = null
    private var saveNoteButton: MenuItem? = null

    private var wasInit = false
    private var storedEnableLineWrap = true
    private var showSaveButton = false
    private var showUndoButton = false
    private var showRedoButton = false
    private var searchIndex = 0
    private var searchMatches = emptyList<Int>()
    private var isSearchActive = false
    private var wasNoteTextEmpty: Boolean = false

    private lateinit var searchQueryET: MyEditText
    private lateinit var searchPrevBtn: ImageView
    private lateinit var searchNextBtn: ImageView
    private lateinit var searchClearBtn: ImageView

    private val binding by viewBinding(ActivityMainBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        refreshMenuItems()

        updateMaterialActivityViews(
            mainCoordinatorLayout = binding.mainCoordinator,
            nestedView = null,
            useTransparentNavigation = false,
            useTopSearchMenu = false
        )

        searchQueryET = findViewById(org.fossify.commons.R.id.search_query)
        searchPrevBtn = findViewById(org.fossify.commons.R.id.search_previous)
        searchNextBtn = findViewById(org.fossify.commons.R.id.search_next)
        searchClearBtn = findViewById(org.fossify.commons.R.id.search_clear)

        val noteToOpen = intent.getLongExtra(OPEN_NOTE_ID, -1L)
        initViewPager(noteToOpen)
        binding.pagerTabStrip.drawFullUnderline = false
        val textSize = getPercentageFontSize()
        binding.pagerTabStrip.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        binding.pagerTabStrip.layoutParams.height =
            (textSize + resources.getDimension(org.fossify.commons.R.dimen.medium_margin) * 2).toInt()
        (binding.pagerTabStrip.layoutParams as ViewPager.LayoutParams).isDecor = true

        val hasNoIntent = intent.action.isNullOrEmpty() && noteToOpen == -1L

        checkWhatsNewDialog()
        checkIntents(intent)

        storeStateVariables()
        if (config.showNotePicker && savedInstanceState == null && hasNoIntent) {
            displayOpenNoteDialog()
        }

        wasInit = true

        checkAppOnSDCard()
        setupSearchButtons()
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.mainToolbar)
        if (storedEnableLineWrap != config.enableLineWrap) {
            initViewPager()
        }

        NotesHelper(this).getNotes { latestNotes ->
            if (mNotes.size != latestNotes.size) {
                initViewPager()
            }
        }

        refreshMenuItems()
        binding.pagerTabStrip.apply {
            val textSize = getPercentageFontSize()
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
            layoutParams.height =
                (textSize + resources.getDimension(org.fossify.commons.R.dimen.medium_margin) * 2).toInt()
            setGravity(Gravity.CENTER_VERTICAL)
            setNonPrimaryAlpha(0.4f)
            setTextColor(getProperPrimaryColor())
            tabIndicatorColor = getProperPrimaryColor()
        }
        updateTextColors(binding.viewPager)

        checkShortcuts()

        binding.searchWrapper.setBackgroundColor(getProperStatusBarColor())
        val contrastColor = getProperPrimaryColor().getContrastColor()
        arrayListOf(searchPrevBtn, searchNextBtn, searchClearBtn).forEach {
            it.applyColorFilter(contrastColor)
        }

        updateTopBarColors(binding.mainToolbar, getProperBackgroundColor())
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            NotesDatabase.destroyInstance()
        }
    }

    private fun refreshMenuItems() {
        val multipleNotesExist = mNotes.size > 1
        val isCurrentItemChecklist = isCurrentItemChecklist()
        val isDefaultEmptyNote = isDefaultEmptyNote()

        binding.mainToolbar.menu.apply {
            findItem(R.id.undo).apply {
                isVisible = showUndoButton && mCurrentNote.type == NoteType.TYPE_TEXT
                icon?.alpha = if (isEnabled) 255 else 127
            }

            findItem(R.id.redo).apply {
                isVisible = showRedoButton && mCurrentNote.type == NoteType.TYPE_TEXT
                icon?.alpha = if (isEnabled) 255 else 127
            }

            findItem(R.id.rename_note).isVisible = multipleNotesExist
            findItem(R.id.open_note).isVisible = multipleNotesExist
            findItem(R.id.open_search).isVisible = !isCurrentItemChecklist
            findItem(R.id.remove_done_items).isVisible = isCurrentItemChecklist
            findItem(R.id.uncheck_all_items).isVisible = isCurrentItemChecklist
            findItem(R.id.sort_checklist).isVisible = isCurrentItemChecklist
            findItem(R.id.import_folder).isVisible = !isQPlus()
            findItem(R.id.lock_note).isVisible =
                mNotes.isNotEmpty() && (::mCurrentNote.isInitialized && !mCurrentNote.isLocked())
            findItem(R.id.unlock_note).isVisible =
                mNotes.isNotEmpty() && (::mCurrentNote.isInitialized && mCurrentNote.isLocked())
            findItem(R.id.more_apps_from_us).isVisible =
                !resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)
            findItem(R.id.delete_note).isVisible = !isDefaultEmptyNote || mNotes.size > 1

            saveNoteButton = findItem(R.id.save_note)
            saveNoteButton!!.isVisible =
                !config.autosaveNotes && showSaveButton && (::mCurrentNote.isInitialized && mCurrentNote.type == NoteType.TYPE_TEXT)
        }

        binding.pagerTabStrip.beVisibleIf(multipleNotesExist)
    }

    private fun setupOptionsMenu() {
        binding.mainToolbar.setOnMenuItemClickListener { menuItem ->
            if (config.autosaveNotes && menuItem.itemId != R.id.undo && menuItem.itemId != R.id.redo) {
                saveCurrentNote(false) {
                    mCurrentNote = it
                }
            }

            val fragment = getCurrentFragment()
            when (menuItem.itemId) {
                R.id.open_search -> fragment?.handleUnlocking { openSearch() }
                R.id.open_note -> displayOpenNoteDialog()
                R.id.save_note -> fragment?.handleUnlocking { saveNote() }
                R.id.undo -> undo()
                R.id.redo -> redo()
                R.id.new_note -> displayNewNoteDialog()
                R.id.rename_note -> fragment?.handleUnlocking { displayRenameDialog() }
                R.id.share -> fragment?.handleUnlocking { shareText() }
                R.id.cab_create_shortcut -> createShortcut()
                R.id.lock_note -> lockNote()
                R.id.unlock_note -> unlockNote()
                R.id.open_file -> tryOpenFile()
                R.id.import_folder -> openFolder()
                R.id.export_as_file -> fragment?.handleUnlocking { tryExportAsFile() }
                R.id.print -> fragment?.handleUnlocking { printText() }
                R.id.delete_note -> fragment?.handleUnlocking { displayDeleteNotePrompt() }
                R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                R.id.remove_done_items -> fragment?.handleUnlocking { removeDoneItems() }
                R.id.uncheck_all_items -> fragment?.handleUnlocking { uncheckAllItems() }
                R.id.sort_checklist -> fragment?.handleUnlocking { displaySortChecklistDialog() }
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    // https://code.google.com/p/android/issues/detail?id=191430 quickfix
    override fun onActionModeStarted(mode: ActionMode?) {
        super.onActionModeStarted(mode)
        if (wasInit) {
            currentNotesView()?.apply {
                if (config.clickableLinks || movementMethod is LinkMovementMethod || movementMethod is MyMovementMethod) {
                    movementMethod = ArrowKeyMovementMethod.getInstance()
                    noteViewWithTextSelected = this
                }
            }
        }
    }

    override fun onActionModeFinished(mode: ActionMode?) {
        super.onActionModeFinished(mode)
        if (config.clickableLinks) {
            noteViewWithTextSelected?.movementMethod = MyMovementMethod.getInstance()
        }
    }

    override fun onBackPressed() {
        if (!config.autosaveNotes && mAdapter?.anyHasUnsavedChanges() == true) {
            ConfirmationAdvancedDialog(
                activity = this,
                message = "",
                messageId = R.string.unsaved_changes_warning,
                positive = org.fossify.commons.R.string.save,
                negative = org.fossify.commons.R.string.discard
            ) {
                if (it) {
                    mAdapter?.saveAllFragmentTexts()
                }
                appLockManager.lock()
                super.onBackPressed()
            }
        } else if (isSearchActive) {
            closeSearch()
        } else {
            appLockManager.lock()
            super.onBackPressed()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val wantedNoteId = intent.getLongExtra(OPEN_NOTE_ID, -1L)
        binding.viewPager.currentItem = getWantedNoteIndex(wantedNoteId)
        checkIntents(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (resultCode != RESULT_OK || resultData?.data == null) return

        val dataUri = resultData.data!!
        when (requestCode) {
            PICK_OPEN_FILE_INTENT -> importUri(dataUri)
            PICK_EXPORT_FILE_INTENT -> if (mNotes.isNotEmpty()) {
                applicationContext.contentResolver.takePersistableUriPermission(
                    dataUri, FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION
                )
                showExportFilePickUpdateDialog(resultData.dataString!!, getCurrentNoteValue())
            }
        }
    }

    private fun isCurrentItemChecklist(): Boolean {
        return if (::mCurrentNote.isInitialized) {
            mCurrentNote.type == NoteType.TYPE_CHECKLIST
        } else {
            false
        }
    }

    private fun isDefaultEmptyNote(): Boolean {
        return if (::mCurrentNote.isInitialized) {
            (mCurrentNote.title == getString(R.string.general_note) &&
                getCurrentNoteText().isNullOrEmpty() &&
                mCurrentNote.value.isEmpty())
        } else {
            false
        }
    }

    @SuppressLint("NewApi")
    private fun checkShortcuts() {
        val appIconColor = config.appIconColor
        if (config.lastHandledShortcutColor != appIconColor) {
            val newTextNote = getNewTextNoteShortcut(appIconColor)
            val newChecklist = getNewChecklistShortcut(appIconColor)

            try {
                shortcutManager.dynamicShortcuts = listOf(newTextNote, newChecklist)
                config.lastHandledShortcutColor = appIconColor
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getNewTextNoteShortcut(appIconColor: Int): ShortcutInfo {
        val shortLabel = getString(R.string.text_note)
        val longLabel = getString(R.string.new_text_note)
        val drawable = AppCompatResources.getDrawable(
            this, org.fossify.commons.R.drawable.shortcut_plus
        )

        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_plus_background)
            .applyColorFilter(appIconColor)
        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, MainActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        intent.putExtra(NEW_TEXT_NOTE, true)
        return ShortcutInfo.Builder(this, SHORTCUT_NEW_TEXT_NOTE)
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .build()
    }

    @SuppressLint("NewApi")
    private fun getNewChecklistShortcut(appIconColor: Int): ShortcutInfo {
        val shortLabel = getString(R.string.checklist)
        val longLabel = getString(R.string.new_checklist)
        val drawable = AppCompatResources.getDrawable(this, R.drawable.shortcut_check)
        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_plus_background)
            .applyColorFilter(appIconColor)
        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, MainActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        intent.putExtra(NEW_CHECKLIST, true)
        return ShortcutInfo.Builder(this, SHORTCUT_NEW_CHECKLIST)
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .build()
    }

    private fun checkIntents(intent: Intent) {
        intent.apply {
            if (action == Intent.ACTION_SEND && type == MIME_TEXT_PLAIN) {
                getStringExtra(Intent.EXTRA_TEXT)?.let {
                    handleTextIntent(it)
                    intent.removeExtra(Intent.EXTRA_TEXT)
                }
            }

            if (action == Intent.ACTION_VIEW) {
                val realPath = intent.getStringExtra(REAL_FILE_PATH)
                val isFromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
                if (!isFromHistory) {
                    if (realPath != null && hasPermission(PERMISSION_READ_STORAGE)) {
                        val file = File(realPath)
                        handleUri(Uri.fromFile(file))
                    } else if (intent.getBooleanExtra(NEW_TEXT_NOTE, false)) {
                        addNewNote(
                            Note(
                                id = null,
                                title = getCurrentFormattedDateTime(),
                                value = "",
                                type = NoteType.TYPE_TEXT,
                                path = "",
                                protectionType = PROTECTION_NONE,
                                protectionHash = ""
                            )
                        )
                    } else if (intent.getBooleanExtra(NEW_CHECKLIST, false)) {
                        addNewNote(
                            Note(
                                id = null,
                                title = getCurrentFormattedDateTime(),
                                value = "",
                                type = NoteType.TYPE_CHECKLIST,
                                path = "",
                                protectionType = PROTECTION_NONE,
                                protectionHash = ""
                            )
                        )
                    } else {
                        handleUri(data!!)
                    }
                }
                intent.removeCategory(Intent.CATEGORY_DEFAULT)
                intent.action = null
                intent.removeExtra(NEW_CHECKLIST)
                intent.removeExtra(NEW_TEXT_NOTE)
            }
        }
    }

    private fun storeStateVariables() {
        config.apply {
            storedEnableLineWrap = enableLineWrap
        }
    }

    private fun handleTextIntent(text: String) {
        NotesHelper(this).getNotes {
            val notes = it
            val list = arrayListOf<RadioItem>().apply {
                add(RadioItem(0, getString(R.string.create_new_note)))
                notes.forEachIndexed { index, note ->
                    add(RadioItem(index + 1, note.title))
                }
            }

            RadioGroupDialog(this, list, -1, R.string.add_to_note) {
                if (it as Int == 0) {
                    displayNewNoteDialog(text)
                } else {
                    updateSelectedNote(notes[it - 1].id!!)
                    addTextToCurrentNote(if (mCurrentNote.value.isEmpty()) text else "\n$text")
                }
            }
        }
    }

    private fun handleUri(uri: Uri) {
        NotesHelper(this).getNoteIdWithPath(uri.path!!) {
            if (it != null && it > 0L) {
                updateSelectedNote(it)
                return@getNoteIdWithPath
            }

            NotesHelper(this).getNotes {
                mNotes = it
                importUri(uri)
            }
        }
    }

    private fun initViewPager(wantedNoteId: Long? = null) {
        NotesHelper(this).getNotes { notes ->
            notes.filter { it.shouldBeUnlocked(this) }
                .forEach(::removeProtection)

            mNotes = notes
            mCurrentNote = mNotes[0]
            wasNoteTextEmpty = mCurrentNote.value.isEmpty()
            mAdapter = NotesPagerAdapter(supportFragmentManager, mNotes, this)
            binding.viewPager.apply {
                adapter = mAdapter
                currentItem = getWantedNoteIndex(wantedNoteId)
                config.currentNoteId = mCurrentNote.id!!

                onPageChangeListener {
                    mCurrentNote = mNotes[it]
                    config.currentNoteId = mCurrentNote.id!!
                    refreshMenuItems()
                }
            }

            if (!config.showKeyboard || mCurrentNote.type == NoteType.TYPE_CHECKLIST) {
                hideKeyboard()
            }
            refreshMenuItems()
        }
    }

    private fun setupSearchButtons() {
        searchQueryET.onTextChangeListener {
            searchTextChanged(it)
        }

        searchPrevBtn.setOnClickListener {
            goToPrevSearchResult()
        }

        searchNextBtn.setOnClickListener {
            goToNextSearchResult()
        }

        searchClearBtn.setOnClickListener {
            closeSearch()
        }

        binding.viewPager.onPageChangeListener {
            currentTextFragment?.removeTextWatcher()
            currentNotesView()?.let { noteView ->
                noteView.text!!.clearBackgroundSpans()
            }

            closeSearch()
            currentTextFragment?.setTextWatcher()
        }

        searchQueryET.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchNextBtn.performClick()
                return@OnEditorActionListener true
            }

            false
        })
    }

    private fun searchTextChanged(text: String) {
        currentNotesView()?.let { noteView ->
            currentTextFragment?.removeTextWatcher()
            noteView.text!!.clearBackgroundSpans()

            if (text.isNotBlank() && text.length > 1) {
                searchMatches = noteView.value.searchMatches(text)
                noteView.highlightText(text, getProperPrimaryColor())
            }

            currentTextFragment?.setTextWatcher()

            if (searchMatches.isNotEmpty()) {
                noteView.requestFocus()
                noteView.setSelection(searchMatches.getOrNull(searchIndex) ?: 0)
            }

            searchQueryET.postDelayed({
                searchQueryET.requestFocus()
            }, 50)
        }
    }

    private fun goToPrevSearchResult() {
        currentNotesView()?.let { noteView ->
            if (searchIndex > 0) {
                searchIndex--
            } else {
                searchIndex = searchMatches.lastIndex
            }

            selectSearchMatch(noteView)
        }
    }

    private fun goToNextSearchResult() {
        currentNotesView()?.let { noteView ->
            if (searchIndex < searchMatches.lastIndex) {
                searchIndex++
            } else {
                searchIndex = 0
            }

            selectSearchMatch(noteView)
        }
    }

    private fun getCurrentFragment() = mAdapter?.getFragment(binding.viewPager.currentItem)

    private val currentTextFragment: TextFragment? get() = mAdapter?.textFragment(binding.viewPager.currentItem)

    private fun selectSearchMatch(editText: MyEditText) {
        if (searchMatches.isNotEmpty()) {
            editText.requestFocus()
            editText.setSelection(searchMatches.getOrNull(searchIndex) ?: 0)
        } else {
            hideKeyboard()
        }
    }

    private fun openSearch() {
        isSearchActive = true
        binding.searchWrapper.fadeIn()
        showKeyboard(searchQueryET)

        currentNotesView()?.let { noteView ->
            noteView.requestFocus()
            noteView.setSelection(0)
        }

        searchQueryET.postDelayed({
            searchQueryET.requestFocus()
        }, 250)
    }

    private fun closeSearch() {
        searchQueryET.text?.clear()
        isSearchActive = false
        binding.searchWrapper.fadeOut()
        hideKeyboard()
    }

    private fun getWantedNoteIndex(wantedNoteId: Long?): Int {
        intent.removeExtra(OPEN_NOTE_ID)
        val noteIdToOpen =
            if (wantedNoteId == null || wantedNoteId == -1L) config.currentNoteId else wantedNoteId
        return getNoteIndexWithId(noteIdToOpen)
    }

    private fun currentNotesView(): MyEditText? {
        return mAdapter?.getCurrentNotesView(binding.viewPager.currentItem)
    }

    private fun displayRenameDialog() {
        RenameNoteDialog(this, mCurrentNote, getCurrentNoteText()) {
            mCurrentNote = it
            initViewPager(mCurrentNote.id)
        }
    }

    private fun updateSelectedNote(id: Long) {
        config.currentNoteId = id
        if (mNotes.isEmpty()) {
            NotesHelper(this).getNotes {
                mNotes = it
                updateSelectedNote(id)
            }
        } else {
            val index = getNoteIndexWithId(id)
            binding.viewPager.currentItem = index
            mCurrentNote = mNotes[index]
        }
    }

    private fun displayNewNoteDialog(
        value: String = "",
        title: String? = null,
        path: String = "",
        setChecklistAsDefault: Boolean = false,
    ) {
        NewNoteDialog(this, title, setChecklistAsDefault) {
            it.value = value
            it.path = path
            addNewNote(it)
        }
    }

    private fun addNewNote(note: Note) {
        NotesHelper(this).insertOrUpdateNote(note) {
            val newNoteId = it
            showSaveButton = false
            showUndoButton = false
            showRedoButton = false
            initViewPager(newNoteId)
            updateSelectedNote(newNoteId)
            binding.viewPager.onGlobalLayout {
                mAdapter?.focusEditText(getNoteIndexWithId(newNoteId))
            }
        }
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses = LICENSE_RTL

        val faqItems = arrayListOf(
            FAQItem(
                title = org.fossify.commons.R.string.faq_1_title_commons,
                text = org.fossify.commons.R.string.faq_1_text_commons
            ),
            FAQItem(
                title = R.string.faq_1_title,
                text = R.string.faq_1_text
            )
        )

        if (!resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)) {
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_2_title_commons,
                    text = org.fossify.commons.R.string.faq_2_text_commons
                )
            )
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_6_title_commons,
                    text = org.fossify.commons.R.string.faq_6_text_commons
                )
            )
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_7_title_commons,
                    text = org.fossify.commons.R.string.faq_7_text_commons
                )
            )
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_10_title_commons,
                    text = org.fossify.commons.R.string.faq_10_text_commons
                )
            )
        }

        startAboutActivity(
            appNameId = R.string.app_name,
            licenseMask = licenses,
            versionName = BuildConfig.VERSION_NAME,
            faqItems = faqItems,
            showFAQBeforeMail = true
        )
    }

    private fun tryOpenFile() {
        hideKeyboard()
        if (hasPermission(PERMISSION_READ_STORAGE)) {
            openFile()
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"

                try {
                    val mimetypes = arrayOf("text/*", "application/json")
                    putExtra(Intent.EXTRA_MIME_TYPES, mimetypes)
                    startActivityForResult(this, PICK_OPEN_FILE_INTENT)
                } catch (e: ActivityNotFoundException) {
                    toast(org.fossify.commons.R.string.system_service_disabled, Toast.LENGTH_LONG)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        }
    }

    private fun openFile() {
        FilePickerDialog(this, canAddShowHiddenButton = true) {
            checkFile(it, true) {
                ensureBackgroundThread {
                    val fileText = it.readText().trim()
                    val checklistItems = fileText.parseChecklistItems()
                    if (checklistItems != null) {
                        val title = it.absolutePath.getFilenameFromPath().substringBeforeLast('.')
                        val note = Note(
                            id = null,
                            title = title,
                            value = fileText,
                            type = NoteType.TYPE_CHECKLIST,
                            path = "",
                            protectionType = PROTECTION_NONE,
                            protectionHash = ""
                        )
                        runOnUiThread {
                            OpenFileDialog(this, it.path) {
                                displayNewNoteDialog(
                                    note.value,
                                    title = it.title,
                                    it.path,
                                    setChecklistAsDefault = true
                                )
                            }
                        }
                    } else {
                        runOnUiThread {
                            OpenFileDialog(this, it.path) {
                                displayNewNoteDialog(
                                    value = it.value,
                                    title = it.title,
                                    path = it.path
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkFile(
        path: String,
        checkTitle: Boolean,
        onChecksPassed: (file: File) -> Unit,
    ) {
        val file = File(path)
        if (path.isMediaFile()) {
            toast(org.fossify.commons.R.string.invalid_file_format)
        } else if (file.length() > 1000 * 1000) {
            toast(R.string.file_too_large)
        } else if (checkTitle && mNotes.any { it.title.equals(path.getFilenameFromPath(), true) }) {
            toast(R.string.title_taken)
        } else {
            onChecksPassed(file)
        }
    }

    private fun checkUri(uri: Uri, onChecksPassed: () -> Unit) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                if (inputStream.available() > 1000 * 1000) {
                    toast(R.string.file_too_large)
                } else {
                    onChecksPassed()
                }
            } ?: return
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun openFolder(path: String, onChecksPassed: (file: File) -> Unit) {
        val file = File(path)
        if (file.isDirectory) {
            onChecksPassed(file)
        }
    }

    private fun importUri(uri: Uri) {
        when (uri.scheme) {
            "file" -> openPath(uri.path!!)
            "content" -> {
                val realPath = getRealPathFromURI(uri)
                if (hasPermission(PERMISSION_READ_STORAGE)) {
                    if (realPath != null) {
                        openPath(realPath)
                    } else {
                        org.fossify.commons.R.string.unknown_error_occurred
                    }
                } else if (realPath != null && realPath != "") {
                    checkFile(realPath, false) {
                        addNoteFromUri(uri, realPath.getFilenameFromPath())
                    }
                } else {
                    checkUri(uri) {
                        addNoteFromUri(uri)
                    }
                }
            }
        }
    }

    private fun addNoteFromUri(uri: Uri, filename: String? = null) {
        val noteTitle = when {
            filename?.isEmpty() == false -> filename
            uri.toString().startsWith("content://") -> getFilenameFromContentUri(uri)
                ?: getNewNoteTitle()

            else -> getNewNoteTitle()
        }

        val inputStream = contentResolver.openInputStream(uri)
        val content = inputStream?.bufferedReader().use { it!!.readText() }
        val checklistItems = content.parseChecklistItems()

        // if we got here by some other app invoking the file open intent, we have no permission for updating the original file itself
        // we can do it only after using "Export as file" or "Open file" from our app
        val canSyncNoteWithFile = if (hasPermission(PERMISSION_WRITE_STORAGE)) {
            true
        } else {
            try {
                val takeFlags = FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION
                applicationContext.contentResolver.takePersistableUriPermission(uri, takeFlags)
                true
            } catch (e: Exception) {
                false
            }
        }

        val noteType = if (checklistItems != null) NoteType.TYPE_CHECKLIST else NoteType.TYPE_TEXT
        if (!canSyncNoteWithFile) {
            val note = Note(
                id = null,
                title = noteTitle,
                value = content,
                type = noteType,
                path = "",
                protectionType = PROTECTION_NONE,
                protectionHash = ""
            )

            displayNewNoteDialog(note.value, title = noteTitle, "")
        } else {
            val items = arrayListOf(
                RadioItem(IMPORT_FILE_SYNC, getString(R.string.update_file_at_note)),
                RadioItem(IMPORT_FILE_NO_SYNC, getString(R.string.only_import_file_content))
            )

            RadioGroupDialog(this, items) {
                val syncFile = it as Int == IMPORT_FILE_SYNC
                val path = if (syncFile) uri.toString() else ""
                val note = Note(
                    id = null,
                    title = noteTitle,
                    value = content,
                    type = noteType,
                    path = "",
                    protectionType = PROTECTION_NONE,
                    protectionHash = ""
                )
                displayNewNoteDialog(value = note.value, title = noteTitle, path = path)
            }
        }
    }

    private fun openPath(path: String) {
        checkFile(path, false) {
            val title = path.getFilenameFromPath()
            try {
                val fileText = it.readText().trim()
                val checklistItems = fileText.parseChecklistItems()
                val note = if (checklistItems != null) {
                    Note(
                        id = null,
                        title = title.substringBeforeLast('.'),
                        value = fileText,
                        type = NoteType.TYPE_CHECKLIST,
                        path = "",
                        protectionType = PROTECTION_NONE,
                        protectionHash = ""
                    )
                } else {
                    Note(
                        id = null,
                        title = title,
                        value = "",
                        type = NoteType.TYPE_TEXT,
                        path = path,
                        protectionType = PROTECTION_NONE,
                        protectionHash = ""
                    )
                }

                if (mNotes.any { it.title.equals(note.title, true) }) {
                    note.title += " (file)"
                }

                addNewNote(note)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    private fun openFolder() {
        handlePermission(PERMISSION_READ_STORAGE) { hasPermission ->
            if (hasPermission) {
                FilePickerDialog(this, pickFile = false, canAddShowHiddenButton = true) {
                    openFolder(it) {
                        ImportFolderDialog(this, it.path) {
                            NotesHelper(this).getNotes {
                                mNotes = it
                                showSaveButton = false
                                initViewPager()
                            }
                        }
                    }
                }
            } else {
                toast(org.fossify.commons.R.string.no_storage_permissions)
            }
        }
    }

    private fun getNewNoteTitle(): String {
        val base = getString(R.string.text_note)
        var i = 1
        while (true) {
            val tryTitle = "$base $i"
            if (mNotes.none { it.title == tryTitle }) {
                return tryTitle
            }
            i++
        }
    }

    private fun tryExportAsFile() {
        hideKeyboard()
        if (hasPermission(PERMISSION_WRITE_STORAGE)) {
            exportAsFile()
        } else {
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "text/*"
                putExtra(Intent.EXTRA_TITLE, "${mCurrentNote.title.removeSuffix(".txt")}.txt")
                addCategory(Intent.CATEGORY_OPENABLE)

                try {
                    startActivityForResult(this, PICK_EXPORT_FILE_INTENT)
                } catch (e: ActivityNotFoundException) {
                    toast(org.fossify.commons.R.string.system_service_disabled, Toast.LENGTH_LONG)
                } catch (e: NetworkErrorException) {
                    toast(getString(R.string.cannot_load_over_internet), Toast.LENGTH_LONG)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        }
    }

    private fun exportAsFile() {
        ExportFileDialog(this, mCurrentNote) {
            val textToExport = if (mCurrentNote.type == NoteType.TYPE_TEXT) {
                getCurrentNoteText()
            } else {
                mCurrentNote.value
            }

            if (textToExport == null || textToExport.isEmpty()) {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
            } else if (mCurrentNote.type == NoteType.TYPE_TEXT) {
                showExportFilePickUpdateDialog(it, textToExport)
            } else {
                tryExportNoteValueToFile(
                    path = it,
                    title = mCurrentNote.title,
                    content = textToExport,
                    showSuccessToasts = true
                )
            }
        }
    }

    private fun showExportFilePickUpdateDialog(exportPath: String, textToExport: String) {
        val items = arrayListOf(
            RadioItem(EXPORT_FILE_SYNC, getString(R.string.update_file_at_note)),
            RadioItem(EXPORT_FILE_NO_SYNC, getString(R.string.only_export_file_content))
        )

        RadioGroupDialog(this, items) {
            val syncFile = it as Int == EXPORT_FILE_SYNC
            tryExportNoteValueToFile(
                path = exportPath,
                title = mCurrentNote.title,
                content = textToExport,
                showSuccessToasts = true
            ) { exportedSuccessfully ->
                if (exportedSuccessfully) {
                    if (syncFile) {
                        mCurrentNote.path = exportPath
                        mCurrentNote.value = ""
                    } else {
                        mCurrentNote.path = ""
                        mCurrentNote.value = textToExport
                    }

                    getPagerAdapter().updateCurrentNoteData(
                        position = binding.viewPager.currentItem,
                        path = mCurrentNote.path,
                        value = mCurrentNote.value
                    )
                    NotesHelper(this).insertOrUpdateNote(mCurrentNote)
                }
            }
        }
    }

    fun tryExportNoteValueToFile(
        path: String,
        title: String,
        content: String,
        showSuccessToasts: Boolean,
        callback: ((success: Boolean) -> Unit)? = null,
    ) {
        if (path.startsWith("content://")) {
            exportNoteValueToUri(
                uri = path.toUri(),
                title = title,
                content = content,
                showSuccessToasts = showSuccessToasts,
                callback = callback
            )
        } else {
            handlePermission(PERMISSION_WRITE_STORAGE) {
                if (it) {
                    exportNoteValueToFile(path, content, showSuccessToasts, callback)
                }
            }
        }
    }

    private fun exportNoteValueToFile(
        path: String,
        content: String,
        showSuccessToasts: Boolean,
        callback: ((success: Boolean) -> Unit)? = null,
    ) {
        try {
            if (File(path).isDirectory) {
                toast(org.fossify.commons.R.string.name_taken)
                return
            }

            if (needsStupidWritePermissions(path)) {
                handleSAFDialog(path) {
                    val document = if (File(path).exists()) {
                        getDocumentFile(path) ?: return@handleSAFDialog
                    } else {
                        val parent = getDocumentFile(File(path).parent) ?: return@handleSAFDialog
                        parent.createFile("", path.getFilenameFromPath())!!
                    }

                    contentResolver.openOutputStream(document.uri)!!.apply {
                        val byteArray = content.toByteArray(Charset.forName("UTF-8"))
                        write(byteArray, 0, byteArray.size)
                        flush()
                        close()
                    }

                    if (showSuccessToasts) {
                        noteExportedSuccessfully(path.getFilenameFromPath())
                    }
                    callback?.invoke(true)
                }
            } else {
                val file = File(path)
                file.writeText(content)

                if (showSuccessToasts) {
                    noteExportedSuccessfully(path.getFilenameFromPath())
                }
                callback?.invoke(true)
            }
        } catch (e: Exception) {
            showErrorToast(e)
            callback?.invoke(false)
        }
    }

    private fun exportNoteValueToUri(
        uri: Uri,
        title: String,
        content: String,
        showSuccessToasts: Boolean,
        callback: ((success: Boolean) -> Unit)? = null,
    ) {
        try {
            val outputStream = contentResolver.openOutputStream(uri, "rwt")
            outputStream!!.bufferedWriter().use { out ->
                out.write(content)
            }
            if (showSuccessToasts) {
                noteExportedSuccessfully(title)
            }
            callback?.invoke(true)
        } catch (e: Exception) {
            showErrorToast(e)
            callback?.invoke(false)
        }
    }

    private fun noteExportedSuccessfully(title: String) {
        val message = String.format(getString(R.string.note_exported_successfully), title)
        toast(message)
    }

    fun noteSavedSuccessfully(title: String) {
        if (config.displaySuccess) {
            val message = String.format(getString(R.string.note_saved_successfully), title)
            toast(message)
        }
    }

    private fun printText() {
        try {
            val webView = WebView(this)
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                    false

                override fun onPageFinished(view: WebView, url: String) {
                    createWebPrintJob(view)
                }
            }

            webView.loadData(getPrintableText().replace("#", "%23"), "text/plain", "UTF-8")
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun createWebPrintJob(webView: WebView) {
        val jobName = mCurrentNote.title
        val printAdapter = webView.createPrintDocumentAdapter(jobName)

        (getSystemService(PRINT_SERVICE) as? PrintManager)?.apply {
            try {
                print(jobName, printAdapter, PrintAttributes.Builder().build())
            } catch (e: IllegalStateException) {
                showErrorToast(e)
            }
        }
    }

    private fun getPagerAdapter() = binding.viewPager.adapter as NotesPagerAdapter

    private fun getCurrentNoteText(): String? {
        return getPagerAdapter().getCurrentNoteViewText(binding.viewPager.currentItem)
    }

    private fun getCurrentNoteValue(): String {
        return if (mCurrentNote.type == NoteType.TYPE_TEXT) {
            getCurrentNoteText() ?: ""
        } else {
            getPagerAdapter().getNoteChecklistItems(binding.viewPager.currentItem) ?: ""
        }
    }

    private fun getPrintableText(): String {
        return if (mCurrentNote.type == NoteType.TYPE_TEXT) {
            getCurrentNoteText() ?: ""
        } else {
            var printableText = ""
            getPagerAdapter().getNoteChecklistRawItems(binding.viewPager.currentItem)?.forEach {
                printableText += "${it.title}\n\n"
            }
            printableText
        }
    }

    private fun addTextToCurrentNote(text: String) {
        getPagerAdapter().appendText(binding.viewPager.currentItem, text)
    }

    private fun saveCurrentNote(force: Boolean, callback: ((note: Note) -> Unit)? = null) {
        getPagerAdapter().saveCurrentNote(binding.viewPager.currentItem, force, callback)
        if (mCurrentNote.type == NoteType.TYPE_CHECKLIST) {
            mCurrentNote.value = getPagerAdapter()
                .getNoteChecklistItems(binding.viewPager.currentItem) ?: ""
        }
    }

    private fun displayDeleteNotePrompt() {
        DeleteNoteDialog(this, mCurrentNote) {
            if (config.isDeletePasswordProtectionOn) {
                handleDeletePasswordProtection {
                    deleteNote(it, mCurrentNote)
                }
            } else {
                deleteNote(it, mCurrentNote)
            }
        }
    }

    fun deleteNote(deleteFile: Boolean, note: Note) {
        if (mNotes.isEmpty() || note != mCurrentNote) {
            return
        }

        if (!deleteFile) {
            doDeleteNote(mCurrentNote, deleteFile)
        } else {
            handleSAFDialog(mCurrentNote.path) {
                doDeleteNote(mCurrentNote, deleteFile)
            }
        }

        val noteId = note.id
        if (note.type == NoteType.TYPE_CHECKLIST && noteId != null) {
            config.removeOwnSorting(noteId)
        }
    }

    private fun doDeleteNote(note: Note, deleteFile: Boolean) {
        ensureBackgroundThread {
            val currentNoteIndex = mNotes.indexOf(note)

            val noteToRefresh = if (mNotes.size == 1) {
                 null
            } else {
                mNotes[if (currentNoteIndex > 0) currentNoteIndex - 1 else currentNoteIndex + 1]
            }

            notesDB.deleteNote(note)
            widgetsDB.deleteNoteWidgets(note.id!!)

            refreshNotes(noteToRefresh, deleteFile)
        }
    }

    private fun refreshNotes(note: Note?, deleteFile: Boolean) {
        NotesHelper(this).getNotes {
            mNotes = it
            val currentNote = note ?: mNotes[0]
            val noteId = currentNote.id
            updateSelectedNote(noteId!!)
            if (config.widgetNoteId == currentNote.id) {
                config.widgetNoteId = mCurrentNote.id!!
                updateWidgets()
            }

            initViewPager()

            if (deleteFile) {
                deleteFile(FileDirItem(currentNote.path, currentNote.title)) {
                    if (!it) {
                        toast(org.fossify.commons.R.string.unknown_error_occurred)
                    }
                }
            }

            if (it.size == 1 && config.showNotePicker) {
                config.showNotePicker = false
            }
        }
    }

    private fun displayOpenNoteDialog() {
        OpenNoteDialog(this) { noteId, newNote ->
            if (newNote == null) {
                updateSelectedNote(noteId)
            } else {
                addNewNote(newNote)
            }
        }
    }

    private fun saveNote() {
        saveCurrentNote(true)
        showSaveButton = false
        refreshMenuItems()
    }

    private fun undo() {
        mAdapter?.undo(binding.viewPager.currentItem)
    }

    private fun redo() {
        mAdapter?.redo(binding.viewPager.currentItem)
    }

    private fun getNoteIndexWithId(id: Long): Int {
        for (i in 0 until mNotes.count()) {
            if (mNotes[i].id == id) {
                mCurrentNote = mNotes[i]
                return i
            }
        }
        return 0
    }

    private fun shareText() {
        val text = if (mCurrentNote.type == NoteType.TYPE_TEXT) {
            getCurrentNoteText()
        } else {
            mCurrentNote.value
        }

        if (text.isNullOrEmpty()) {
            toast(R.string.cannot_share_empty_text)
            return
        }

        val res = resources
        val shareTitle = res.getString(org.fossify.commons.R.string.share_via)
        Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_SUBJECT, mCurrentNote.title)
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
            startActivity(Intent.createChooser(this, shareTitle))
        }
    }

    @SuppressLint("NewApi")
    private fun createShortcut() {
        val manager = getSystemService(ShortcutManager::class.java)
        if (manager.isRequestPinShortcutSupported) {
            val note = mCurrentNote
            val drawable = AppCompatResources.getDrawable(this, R.drawable.shortcut_note)?.mutate()
            val appIconColor = baseConfig.appIconColor
            (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_plus_background)
                .applyColorFilter(appIconColor)

            val intent = Intent(this, SplashActivity::class.java)
            intent.action = Intent.ACTION_VIEW
            intent.putExtra(OPEN_NOTE_ID, note.id)
            intent.flags =
                intent.flags or FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK or FLAG_ACTIVITY_NO_HISTORY

            val shortcut = ShortcutInfo.Builder(this, note.hashCode().toString())
                .setShortLabel(mCurrentNote.title)
                .setIcon(Icon.createWithBitmap(drawable.convertToBitmap()))
                .setIntent(intent)
                .build()

            manager.requestPinShortcut(shortcut, null)
        }
    }

    private fun lockNote() {
        ConfirmationDialog(
            activity = this,
            message = "",
            messageId = R.string.locking_warning,
            positive = org.fossify.commons.R.string.ok,
            negative = org.fossify.commons.R.string.cancel
        ) {
            SecurityDialog(this, "", SHOW_ALL_TABS) { hash, type, success ->
                if (success) {
                    mCurrentNote.protectionHash = hash
                    mCurrentNote.protectionType = type
                    NotesHelper(this).insertOrUpdateNote(mCurrentNote) {
                        refreshMenuItems()
                    }
                }
            }
        }
    }

    private fun unlockNote() {
        performSecurityCheck(
            protectionType = mCurrentNote.protectionType,
            requiredHash = mCurrentNote.protectionHash,
            successCallback = { _, _ -> removeProtection(mCurrentNote) }
        )
    }

    private fun removeProtection(note: Note) {
        note.protectionHash = ""
        note.protectionType = PROTECTION_NONE
        NotesHelper(this).insertOrUpdateNote(note) {
            if (note == mCurrentNote) {
                getCurrentFragment()?.apply {
                    shouldShowLockedContent = true
                    checkLockState()
                }
                refreshMenuItems()
            }
        }
    }

    fun currentNoteTextChanged(newText: String, showUndo: Boolean, showRedo: Boolean) {
        if (!isSearchActive) {
            var shouldRecreateMenu = false
            if (showUndo != showUndoButton) {
                showUndoButton = showUndo
                shouldRecreateMenu = true
            }

            if (showRedo != showRedoButton) {
                showRedoButton = showRedo
                shouldRecreateMenu = true
            }

            if (!config.autosaveNotes) {
                showSaveButton = newText != mCurrentNote.value
                if (showSaveButton != saveNoteButton?.isVisible) {
                    shouldRecreateMenu = true
                }
            }

            if (getCurrentNoteText().isNullOrEmpty()) {
                wasNoteTextEmpty = true
                shouldRecreateMenu = true
            } else if (wasNoteTextEmpty) {
                wasNoteTextEmpty = false
                shouldRecreateMenu = true
            }

            if (shouldRecreateMenu) {
                refreshMenuItems()
            }
        }
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }

    private fun removeDoneItems() {
        getPagerAdapter().removeDoneCheckListItems(binding.viewPager.currentItem)
    }

    private fun uncheckAllItems() {
        getPagerAdapter().uncheckAllItems(binding.viewPager.currentItem)
    }

    private fun displaySortChecklistDialog() {
        SortChecklistDialog(this, mCurrentNote.id) {
            getPagerAdapter().refreshChecklist(binding.viewPager.currentItem)
            updateWidgets()
        }
    }
}
