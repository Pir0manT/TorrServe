package ru.yourok.torrserve.ui.fragments.main.torrents

import android.app.Activity
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.atv.Utils
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.ui.activities.play.PlayActivity
import ru.yourok.torrserve.ui.fragments.TSFragment
import ru.yourok.torrserve.utils.TorrentHelper


class TorrentsFragment : TSFragment() {

    private var torrentAdapter: TorrentsAdapter? = null
    private lateinit var emptyView: TextView
    private lateinit var etSearchTorrents: TextInputEditText
    private lateinit var searchLayout: TextInputLayout
    private var sortMode: Boolean = Settings.sortTorrByTitle
    private var searchQuery: String = ""
    private var currentCategory: String = ""

    private val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            text?.let { etSearchTorrents.setText(it) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.main_fragment, container, false)
        torrentAdapter = TorrentsAdapter(requireActivity())
        emptyView = vi.findViewById(R.id.empty_view)

        // Search setup
        searchLayout = vi.findViewById(R.id.searchLayout)
        etSearchTorrents = vi.findViewById(R.id.etSearchTorrents)

        searchLayout.setStartIconOnClickListener {
            launchVoiceSearch()
        }

        etSearchTorrents.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                searchQuery = s.toString().trim().lowercase()
                applyFilter()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        etSearchTorrents.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                etSearchTorrents.clearFocus()
                vi.findViewById<ListView>(R.id.lvTorrents)?.requestFocus()
            }
            false
        }

        vi.findViewById<ListView>(R.id.lvTorrents)?.let { lvTorrents ->
            lvTorrents.adapter = torrentAdapter
            lvTorrents.setOnItemClickListener { _, _, i, _ ->
                val torr = torrentAdapter?.getItem(i) as Torrent
                val intent = Intent(App.context, PlayActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                intent.action = Intent.ACTION_VIEW
                intent.putExtra("hash", torr.hash)
                intent.putExtra("title", torr.title)
                torr.category?.let { if (it.isNotBlank()) intent.putExtra("category", it) }
                intent.putExtra("poster", torr.poster)
                intent.putExtra("action", "play")
                App.context.startActivity(intent)
            }
            lvTorrents.choiceMode = ListView.CHOICE_MODE_MULTIPLE_MODAL
            lvTorrents.setMultiChoiceModeListener(TorrentsActionBar(lvTorrents))
            lvTorrents.requestFocus()
        }
        return vi
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            start()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isViewModelInitialized())
            (viewModel as TorrentsViewModel).setUpdate(false)
    }

    private fun launchVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
        }
        try {
            voiceSearchLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            App.toast(R.string.voice_search_unavailable)
        }
    }

    private fun applyFilter() {
        val fullList = (viewModel as TorrentsViewModel).data?.value ?: return
        val query = searchQuery

        val filtered = fullList.filter { torrent ->
            val matchesCategory = currentCategory.isEmpty() ||
                    torrent.category?.contains(currentCategory, true) == true

            val matchesSearch = query.isEmpty() ||
                    torrent.title.lowercase().contains(query) ||
                    torrent.name.lowercase().contains(query) ||
                    torrent.category?.lowercase()?.contains(query) == true ||
                    torrent.data?.lowercase()?.contains(query) == true

            matchesCategory && matchesSearch
        }

        torrentAdapter?.update(filtered)
        emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    fun sort(mode: Boolean = sortMode) {
        val list = torrentAdapter!!.list
        if (list.size > 0) {
            when (mode) {
                false -> {
                    torrentAdapter?.update(list.sortedBy { it.title })
                    App.toast(R.string.sort_by_name)
                }

                true -> {
                    torrentAdapter?.update(list.sortedByDescending { it.timestamp })
                    App.toast(R.string.sort_by_date)
                }
            }
            sortMode = !mode
            Settings.set("sort_torrents", sortMode)
            activity?.findViewById<ListView>(R.id.lvTorrents)?.apply {
                this.setSelection(0)
                requestFocus()
            }
        }
    }

    fun filter(cat: String = "") {
        currentCategory = cat
        applyFilter()
    }

    suspend fun start() = withContext(Dispatchers.Main) {
        viewModel = ViewModelProvider(this@TorrentsFragment)[TorrentsViewModel::class.java]
        val data = (viewModel as TorrentsViewModel).getData()
        (viewModel as TorrentsViewModel).setUpdate(true)
        data.observe(this@TorrentsFragment) {
            applyFilter()
        }
    }

    fun onKeyUp(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_SEARCH -> {
                return true
            }
        }
        return false
    }

    @SuppressLint("NotifyDataSetChanged")
    fun onKeyDown(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_SEARCH -> {
                launchVoiceSearch()
                return true
            }

            KeyEvent.KEYCODE_INFO -> {
                activity?.currentFocus?.let {
                    it.findViewById<ListView>(R.id.lvTorrents)?.let { lv ->
                        val itemPosition = lv.selectedItemPosition
                        if (itemPosition in torrentAdapter!!.list.indices) {
                            torrentAdapter!!.list[itemPosition].let {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val torrent = TorrentHelper.waitFiles(it.hash) ?: let {
                                        return@launch
                                    }
                                    TorrentHelper.showFFPInfo(lv.context, "", torrent)
                                }
                            }
                        }
                    }
                }
                return true
            }

            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_BUTTON_X -> {
                sort(sortMode)
                if (Utils.isTvBox()) return true
            }

        }
        return false
    }
}
