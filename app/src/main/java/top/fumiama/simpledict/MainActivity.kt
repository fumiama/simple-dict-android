package top.fumiama.simpledict

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lapism.search.internal.SearchLayout
import com.lapism.search.util.SearchUtils
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.line_word.view.*

class MainActivity : AppCompatActivity() {
    private val dict = SimpleDict(Client("127.0.0.1", 8000), "fumiama")
    private var hasLiked = false
    private var cm: ClipboardManager? = null
    private var ad: ListViewHolder.RecyclerViewAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ad = LikeViewHolder(ffr).RecyclerViewAdapter()
        cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        ffr.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = ad
            setOnScrollChangeListener { _, _, scrollY, _, _ ->
                ffsw.isEnabled = scrollY == 0
            }
            ffsw.apply {
                setOnRefreshListener {
                    fetchThread()
                }
                isRefreshing = true
                fetchThread()
            }
        }

        ffms.apply {
            val recyclerView = findViewById<RecyclerView>(R.id.search_recycler_view)
            setAdapterLayoutManager(LinearLayoutManager(this@MainActivity))
            val adapter = SearchViewHolder(recyclerView, findViewById(R.id.search_search_edit_text)).RecyclerViewAdapter()
            setAdapter(adapter)
            navigationIconSupport = SearchLayout.NavigationIconSupport.SEARCH
            setOnNavigationClickListener(object : SearchLayout.OnNavigationClickListener {
                override fun onNavigationClick(hasFocus: Boolean) {
                    if (hasFocus()) {
                        if(hasLiked) ad?.refresh()
                        clearFocus()
                    }
                    else requestFocus()
                }
            })
            setTextHint(android.R.string.search_go)
            setOnQueryTextListener(object : SearchLayout.OnQueryTextListener {
                override fun onQueryTextChange(newText: CharSequence): Boolean {
                    if (newText.isNotEmpty()) adapter.refresh()
                    return true
                }

                override fun onQueryTextSubmit(query: CharSequence): Boolean {
                    if(query.isNotEmpty()) {
                        val key = query.toString()
                        val data = dict[key]
                        showDictAlert(key, data, recyclerView.children.toList().let {
                            val i = it.map { it.ta.text }.indexOf(key)
                            if(i >= 0) it[i] else null
                        }) { adapter.refresh() }
                    }
                    return true
                }
            })
            setOnMicClickListener(object : SearchLayout.OnMicClickListener {
                override fun onMicClick() {
                    if (SearchUtils.isVoiceSearchAvailable(this@MainActivity)) {
                        SearchUtils.setVoiceSearch(this@MainActivity, "please speak")
                    }
                }
            })
            setOnFocusChangeListener(object : SearchLayout.OnFocusChangeListener {
                override fun onFocusChange(hasFocus: Boolean) {
                    navigationIconSupport = if (hasFocus) SearchLayout.NavigationIconSupport.ARROW
                    else SearchLayout.NavigationIconSupport.SEARCH
                }
            })
        }
    }

    override fun onBackPressed() {
        if(ffms.hasFocus()) {
            if(hasLiked) ad?.refresh()
            ffms.clearFocus()
        }
        else super.onBackPressed()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when(requestCode) {
            SearchUtils.SPEECH_REQUEST_CODE -> data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.let {
                if(it.isNotEmpty()) {
                    ffms.requestFocus()
                    ffms.mSearchEditText?.setText(it[0])
                }
            }
        }
    }

    private fun fetchThread() {
        Thread{
            dict.fetchDict {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "刷新成功", Toast.LENGTH_SHORT).show()
                    ffsw.isRefreshing = false
                    ad?.refresh()
                }
            }
        }.start()
    }

    private fun showDictAlert(key: String, data: String?, line: View?, refresh: (()->Unit)?=null) {
        val hintAdd = if(data != null && data != "null") "重设" else "添加"
        hasLiked = false
        AlertDialog.Builder(this@MainActivity)
                .setTitle(key)
                .setMessage(data)
                .setPositiveButton(hintAdd) { _, _ ->
                    val t = EditText(this@MainActivity)
                    t.setText(data)
                    AlertDialog.Builder(this@MainActivity)
                            .setTitle("$hintAdd$key")
                            .setView(t)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                val newText = t.text.toString()
                                if (t.text.isNotEmpty() && newText != data) Thread {
                                    dict[key] = newText
                                    line?.tb?.text = newText
                                }.start()
                                else Toast.makeText(this, "未更改", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton(android.R.string.cancel) { _, _ -> }
                            .show()
                }
                .setNeutralButton("删除") { _, _ ->
                    Thread{
                        dict -= key
                        line?.apply {
                            val delKey = SpannableString(key)
                            val delData = SpannableString(data)
                            delKey.setSpan(StrikethroughSpan(), 0, key.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            delData.setSpan(StrikethroughSpan(), 0, (data?.length?:0), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            ta.text = delKey
                            tn.text = delKey
                            tb.text = delData
                        }
                    }.start()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .show()
    }

    inner class SearchViewHolder(itemView: View, private val editText: EditText) : ListViewHolder(itemView) {
        inner class RecyclerViewAdapter : ListViewHolder.RecyclerViewAdapter() {
            override fun getKeys() = filter(editText.text)
            override fun getValue(key: String) = dict[key]
            private fun filter(text: CharSequence): List<String> {
                val selectSet = dict.keys.filter { it.contains(text, true) }.toSet() +
                        dict.filterValues { it?.contains(text, true) ?: false }.let {
                            val newSet = mutableSetOf<String>()
                            it.keys.forEach {
                                newSet += it
                            }
                            newSet
                        }
                return selectSet.toList().let { if (it.size > 50) it.subList(0, 49) else it }
            }
        }
    }

    inner class LikeViewHolder(itemView: View) : ListViewHolder(itemView) {
        inner class RecyclerViewAdapter: ListViewHolder.RecyclerViewAdapter(){
            override fun getKeys() = getSharedPreferences("dict", MODE_PRIVATE).all.keys.toTypedArray().let{
                val end = dict.latestKeys.size
                val start = if(end > 5) end - 5 else 0
                (dict.latestKeys.copyOfRange(start, end) + it).toList()
            }
            override fun getValue(key: String) = dict[key]?:getSharedPreferences("dict", MODE_PRIVATE).getString(key, "null")
        }
    }

    open inner class ListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        open inner class RecyclerViewAdapter :
            RecyclerView.Adapter<ListViewHolder>() {
            private var listKeys: List<String>? = null
            open fun getKeys(): List<String>? = null
            open fun getValue(key: String): String? = null
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {
                return ListViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.line_word, parent, false)
                )
            }

            @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
            override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
                Log.d("MyMain", "Bind open at $position")
                Thread{
                    listKeys?.apply {
                        if (position < size) {
                            val key = get(position)
                            val data = getValue(key)
                            val like = getSharedPreferences("dict", MODE_PRIVATE)?.contains(key) == true
                            Log.d("MyMain", "Like status of $key is $like")
                            holder.itemView.apply {
                                runOnUiThread {
                                    tn.text = key
                                    ta.text = key
                                    tb.text = data
                                    vl.setBackgroundResource(if(like) R.drawable.ic_like_filled else R.drawable.ic_like)
                                    Log.d("MyMain", "Set like of $key: $like")
                                    setOnClickListener {
                                        showDictAlert(key, data, this)
                                    }
                                    setOnLongClickListener {
                                        cm?.setPrimaryClip(ClipData.newPlainText("SimpleDict", "$key\n$data"))
                                        runOnUiThread {
                                            Toast.makeText(this@MainActivity, "已复制", Toast.LENGTH_SHORT).show()
                                        }
                                        true
                                    }
                                    vl.setOnClickListener {
                                        getSharedPreferences("dict", MODE_PRIVATE)?.edit()?.apply {
                                            if (like) {
                                                remove(key)
                                                it.setBackgroundResource(R.drawable.ic_like)
                                            } else {
                                                putString(key, data)
                                                it.setBackgroundResource(R.drawable.ic_like_filled)
                                            }
                                            hasLiked = true
                                            apply()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }.start()
            }

            override fun getItemCount() = listKeys?.size?:0

            fun refresh() = Thread{
                listKeys = getKeys()
                runOnUiThread { notifyDataSetChanged() }
            }.start()
        }
    }
}