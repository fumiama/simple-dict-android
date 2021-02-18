package top.fumiama.simpledict

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lapism.search.internal.SearchLayout
import com.lapism.search.util.SearchUtils
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.line_word.view.*

class MainActivity : AppCompatActivity() {
    private val dict = SimpleDict(Client("127.0.0.1", 8000), "fumiama")
    private var hasLiked = false
    private val fetchThread get() = Thread{
        dict.fetchDict {
            runOnUiThread {
                Toast.makeText(this@MainActivity, "刷新成功", Toast.LENGTH_SHORT).show()
                ffsw.isRefreshing = false
            }
        }
    }
    private var cm: ClipboardManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val ad = LikeViewHolder(ffr).RecyclerViewAdapter()
        cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        ffr.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = ad
            setOnScrollChangeListener { _, _, scrollY, _, _ ->
                ffsw.isEnabled = scrollY == 0
            }
            ffsw.apply {
                setOnRefreshListener {
                    ad.refresh()
                    fetchThread.start()
                }
                isRefreshing = true
                fetchThread.start()
            }
        }

        ffms.apply {
            setAdapterLayoutManager(LinearLayoutManager(this@MainActivity))
            val adapter = SearchViewHolder(findViewById(R.id.search_recycler_view)).RecyclerViewAdapter()
            setAdapter(adapter)
            navigationIconSupport = SearchLayout.NavigationIconSupport.SEARCH
            setOnNavigationClickListener(object : SearchLayout.OnNavigationClickListener {
                override fun onNavigationClick(hasFocus: Boolean) {
                    if (hasFocus()) {
                        if(hasLiked) ad.refresh()
                        clearFocus()
                    }
                    else requestFocus()
                }
            })
            setTextHint(android.R.string.search_go)
            setOnQueryTextListener(object : SearchLayout.OnQueryTextListener {
                override fun onQueryTextChange(newText: CharSequence): Boolean {
                    if (newText.isNotEmpty()) adapter.filter(newText)
                    return true
                }

                override fun onQueryTextSubmit(query: CharSequence): Boolean {
                    if(query.isNotEmpty()) {
                        val key = query.toString()
                        val data = dict[key]
                        showDictAlert(key, data)
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
            if(hasLiked) (ffr.adapter as ListViewHolder.RecyclerViewAdapter).refresh()
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

    private fun showDictAlert(key: String, data: String?) {
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
                                if (t.text.isNotEmpty() && t.text.toString() != data) Thread {
                                    dict[key] = t.text.toString()
                                }.start()
                                else Toast.makeText(this, "未更改", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton(android.R.string.cancel) { _, _ -> }
                            .show()
                }
                .setNeutralButton("删除") { _, _ ->
                    Thread{dict -= key}.start()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .show()
    }

    inner class SearchViewHolder(itemView: View) : ListViewHolder(itemView) {
        inner class RecyclerViewAdapter : ListViewHolder.RecyclerViewAdapter() {
            override fun getValue(key: String) = dict[key]
            fun filter(text: CharSequence) {
                Thread{
                    val selectSet = dict.keys.filter { it.contains(text, true) }.toSet() +
                            dict.filterValues { it?.contains(text, true)?:false }.let {
                                val newSet = mutableSetOf<String>()
                                it.keys.forEach {
                                    newSet += it
                                }
                                newSet
                            }
                    listKeys = selectSet.toList().let { if(it.size > 50) it.subList(0, 49) else it }
                    listKeys?.forEach {
                        Log.d("MyMain", "Select key: $it")
                    }
                    runOnUiThread { notifyDataSetChanged() }
                }.start()
            }
        }
    }

    inner class LikeViewHolder(itemView: View) : ListViewHolder(itemView) {
        inner class RecyclerViewAdapter: ListViewHolder.RecyclerViewAdapter(){
            override fun getKeys() = getSharedPreferences("dict", MODE_PRIVATE).all.keys.toList()
            override fun getValue(key: String) = getSharedPreferences("dict", MODE_PRIVATE).getString(key, "null")
        }
    }

    open inner class ListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        open inner class RecyclerViewAdapter :
            RecyclerView.Adapter<ListViewHolder>() {
            var listKeys = getKeys()
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
                Log.d("MyMain", "Bind like at $position")
                Thread{
                    listKeys?.apply {
                        if (position < size) {
                            val key = get(position)
                            val data = getValue(key)
                            val like = getSharedPreferences("dict", MODE_PRIVATE)?.contains(key) == true
                            runOnUiThread {
                                holder.itemView.apply {
                                    ta.text = key
                                    tb.text = data
                                    if(like) vl.setBackgroundResource(R.drawable.ic_like_filled)
                                    setOnClickListener {
                                        showDictAlert(key, data)
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

            fun refresh() {
                listKeys = getKeys()
                runOnUiThread { notifyDataSetChanged() }
            }
        }
    }
}