package top.fumiama.simpledict

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lapism.search.internal.SearchLayout
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.ffsw
import kotlinx.android.synthetic.main.activity_main.view.*
import kotlinx.android.synthetic.main.dialog_input.view.*
import kotlinx.android.synthetic.main.line_word.view.*
import kotlinx.android.synthetic.main.line_word.view.tb
import kotlinx.android.synthetic.main.line_word.view.tn
import java.io.FileNotFoundException
import java.lang.Exception

class MainActivity : AppCompatActivity() {
    private var host = "127.0.0.1"
    private var port = 80
    private var pwd = "demo"
    private var spwd: String? = null
    private var dict: SimpleDict? = null
    private var hasLiked = false
    private var cm: ClipboardManager? = null
    private var ad: LikeViewHolder.RecyclerViewAdapter? = null
    private var lastLikeLine: View? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val ime = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        getSharedPreferences("remote", MODE_PRIVATE)?.apply {
            if(contains("host")) getString("host", host)?.apply { host = this }
            if(contains("port")) getInt("port", port).apply { port = this }
            if(contains("pwd")) getString("pwd", pwd)?.apply { pwd = this }
            if(contains("spwd")) getString("spwd", spwd)?.apply { spwd = this }
        }
        dict = SimpleDict(Client(host, port), pwd, spwd)
        ad = LikeViewHolder(ffr).RecyclerViewAdapter()
        cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        ffr.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = ad
            setOnScrollChangeListener { _, _, scrollY, _, _ ->
                this@MainActivity.ffsw.isEnabled = scrollY == 0
            }
        }

        ffsw.apply {
            setOnRefreshListener {
                fetchThread{
                    updateSize()
                }
            }
            isRefreshing = true
            fetchThread {
                updateSize()
            }
        }

        ffms.apply {
            val recyclerView = findViewById<RecyclerView>(R.id.search_recycler_view)
            setAdapterLayoutManager(LinearLayoutManager(this@MainActivity))
            val adapter = SearchViewHolder(recyclerView).RecyclerViewAdapter()
            setAdapter(adapter)
            navigationIconSupport = SearchLayout.NavigationIconSupport.SEARCH
            setMicIconImageResource(R.drawable.ic_setting)
            val micView = findViewById<ImageButton>(R.id.search_image_view_mic)
            setClearFocusOnBackPressed(true)
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
                var lastChangeTime = 0L
                override fun onQueryTextChange(newText: CharSequence): Boolean {
                    postDelayed({
                        val diff = System.currentTimeMillis() - lastChangeTime
                        if(diff > 500) {
                            if (newText.isNotEmpty()) adapter.refresh(newText)
                        }
                    }, 1024)
                    lastChangeTime = System.currentTimeMillis()
                    return true
                }

                override fun onQueryTextSubmit(query: CharSequence): Boolean {
                    if(query.isNotEmpty()) {
                        val key = query.toString().trim().replace(Regex("[\\uFF00-\\uFF5E]")) { (it.value[0] - 0xFEE0).toString() }
                        val data = dict?.get(key)
                        showDictAlert(key, data, recyclerView.children.toList().let {
                            val i = it.map { it.ta.text }.indexOf(key)
                            if(i >= 0) it[i] else null
                        })
                    }
                    return true
                }
            })
            setOnMicClickListener(object : SearchLayout.OnMicClickListener {
                override fun onMicClick() {
                    /*if (SearchUtils.isVoiceSearchAvailable(this@MainActivity)) {
                        SearchUtils.setVoiceSearch(this@MainActivity, "please speak")
                    }*/
                    val t = layoutInflater.inflate(R.layout.dialog_input, null, false)
                    AlertDialog.Builder(this@MainActivity)
                            .setView(t)
                            .setTitle("提示")
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                val info = t.diet.text.toString()
                                try {
                                    val h = info.substringBefore(':')
                                    val l = info.substringAfter(':')
                                    val p = l.substringBefore('_').toInt()
                                    var w = l.substringAfter('_')
                                    if (h != "" && p > 0 && p < 65536 && w != "") {
                                        getSharedPreferences("remote", MODE_PRIVATE)?.edit {
                                            putString("host", h)
                                            putInt("port", p)
                                            if(w.contains('^')) {
                                                val s = w.substringAfterLast('^')
                                                if (s != "") {
                                                    putString("spwd", s)
                                                    w = w.substringBeforeLast('^')
                                                }
                                            }
                                            putString("pwd", w)
                                            apply()
                                            Toast.makeText(this@MainActivity, "下次生效", Toast.LENGTH_SHORT).show()
                                            return@setPositiveButton
                                        }?:throw FileNotFoundException("getSharedPreferences named \"remote\" error.")
                                    } else throw IllegalArgumentException()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(this@MainActivity, "格式非法", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton(android.R.string.cancel) { _, _ -> }
                            .show()
                }
            })

            setOnFocusChangeListener(object : SearchLayout.OnFocusChangeListener {
                override fun onFocusChange(hasFocus: Boolean) {
                    navigationIconSupport = if (hasFocus) SearchLayout.NavigationIconSupport.ARROW
                    else {
                        micView.postDelayed({ micView.visibility = View.VISIBLE }, 233)
                        SearchLayout.NavigationIconSupport.SEARCH
                    }
                }
            })

            this@MainActivity.ffc.setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_UP && mSearchEditText?.text?.isNotEmpty() == true) {
                    ime.hideSoftInputFromWindow(window.decorView.windowToken, 0)
                }
                false
            }
        }
    }

    override fun onBackPressed() {
        if(ffms.hasFocus()) {
            if(hasLiked) ad?.refresh()
        } else super.onBackPressed()
    }

    /*override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when(requestCode) {
            SearchUtils.SPEECH_REQUEST_CODE -> data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.let {
                if(it.isNotEmpty()) {
                    ffms.requestFocus()
                    ffms.mSearchEditText?.setText(it[0])
                }
            }
        }
    }*/

    private fun updateSize() = runOnUiThread {
        lastLikeLine?.fftc?.text = dict?.size?.toString()?:"0"
    }

    private fun fetchThread(doWhenFinish: (()->Unit)? = null) {
        Thread{
            dict?.fetchDict({
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "刷新失败", Toast.LENGTH_SHORT).show()
                }
            }, {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "刷新成功", Toast.LENGTH_SHORT).show()
                }
            }) {
                runOnUiThread {
                    ffsw.isRefreshing = false
                    ad?.capacity = 5
                    ad?.refresh()
                    doWhenFinish?.apply { this() }
                }
            }
        }.start()
    }

    private fun showDictAlert(key: String, data: String?, line: View?) {
        val hintAdd = if(data != null && data != "null") "重设" else "添加"
        hasLiked = false
        AlertDialog.Builder(this@MainActivity)
                .setTitle(key)
                .setMessage(data)
                .setPositiveButton(hintAdd) { _, _ ->
                    val t = layoutInflater.inflate(R.layout.dialog_input, null, false)
                    t.diet.setText(data)
                    t.dit.text = "更改将立即生效"
                    AlertDialog.Builder(this@MainActivity)
                            .setTitle("$hintAdd$key")
                            .setView(t)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                val newText = t.diet.text.toString().trim().replace(Regex("[\\uFF00-\\uFF5E]")) { (it.value[0] - 0xFEE0).toString() }
                                if (t.diet.text.isNotEmpty() && newText != data) Thread {
                                    if(dict?.set(key, newText) == true) {
                                        line?.tb?.text = newText
                                        updateSize()
                                    } else runOnUiThread {
                                        Toast.makeText(this, "失败", Toast.LENGTH_SHORT).show()
                                    }
                                }.start()
                                else Toast.makeText(this, "未更改", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton(android.R.string.cancel) { _, _ -> }
                            .show()
                }
                .setNeutralButton("删除") { _, _ ->
                    Thread{
                        if(dict?.del(key) == true) line?.apply {
                            val delKey = SpannableString(key)
                            val delData = SpannableString(data)
                            delKey.setSpan(StrikethroughSpan(), 0, key.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            delData.setSpan(StrikethroughSpan(), 0, (data?.length?:0), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            ta.text = delKey
                            tn.text = delKey
                            tb.text = delData
                            updateSize()
                        }
                        else runOnUiThread {
                            Toast.makeText(this, "失败", Toast.LENGTH_SHORT).show()
                        }
                    }.start()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .show()
    }

    inner class SearchViewHolder(itemView: View) : ListViewHolder(itemView) {
        inner class RecyclerViewAdapter : ListViewHolder.RecyclerViewAdapter() {
            override fun getKeys(filterText: CharSequence?) = filterText?.let { filter(it) }
            override fun getValue(key: String) = dict?.get(key)
            private fun filter(text: CharSequence): List<String> {
                val selectSet = dict?.keys?.filter { it.contains(text, true) }?.toSet()?.plus(dict?.filterValues { it?.contains(text, true) ?: false }.let {
                    val newSet = mutableSetOf<String>()
                    it?.keys?.forEach {
                        newSet += it
                    }
                    newSet
                })
                return selectSet?.toList()?.let { if (it.size > 50) it.subList(0, 49) else it }?: emptyList()
            }
        }
    }

    inner class LikeViewHolder(itemView: View) : ListViewHolder(itemView) {
        inner class RecyclerViewAdapter: ListViewHolder.RecyclerViewAdapter(true){
            var capacity = 5
            override fun loadMore() {
                capacity += 5
                refresh()
            }
            override fun getKeys(filterText: CharSequence?) = getSharedPreferences("dict", MODE_PRIVATE).all.keys.toTypedArray().let{
                dict?.let { d ->
                    val end = d.latestKeys.size
                    val start = if(end > capacity) end - capacity else 0
                    (it + d.latestKeys.copyOfRange(start, end).reversedArray()).toList()
                }?: emptyList()
            }
            override fun getValue(key: String) = dict?.get(key)?:getSharedPreferences("dict", MODE_PRIVATE).getString(key, "null")
        }
    }

    open inner class ListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        open inner class RecyclerViewAdapter(private val showLoadMore: Boolean = false) :
            RecyclerView.Adapter<ListViewHolder>() {
            private var listKeys: List<String>? = null
            open fun getKeys(filterText: CharSequence? = null): List<String>? = null
            open fun getValue(key: String): String? = null
            open fun loadMore() {}
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
                                    ta.visibility = View.VISIBLE
                                    lwclast.visibility = View.GONE
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
                        } else if(showLoadMore && position == size) runOnUiThread{
                            holder.itemView.apply {
                                lastLikeLine = this
                                ta.visibility = View.GONE
                                lwclast.visibility = View.VISIBLE
                                tn.text = "motkyep..."
                                tb.text = "加载更多..."
                                updateSize()
                                setOnClickListener {
                                    loadMore()
                                }
                            }
                        }
                    }
                }.start()
            }

            override fun getItemCount() = (listKeys?.size?:0) + (if(showLoadMore) 1 else 0)

            fun refresh(filterText: CharSequence? = null) = Thread{
                listKeys = getKeys(filterText)
                runOnUiThread { notifyDataSetChanged() }
            }.start()
        }
    }
}