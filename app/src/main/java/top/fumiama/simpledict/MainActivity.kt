package top.fumiama.simpledict

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.children
import androidx.fragment.app.commit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.lapism.search.internal.SearchLayout
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import kotlinx.android.synthetic.main.card_bottom.cbcard
import kotlinx.android.synthetic.main.dialog_input.view.*
import kotlinx.android.synthetic.main.fragment_main.fmvp
import kotlinx.android.synthetic.main.line_bottom.view.*
import kotlinx.android.synthetic.main.line_word.view.*
import java.io.FileNotFoundException

class MainActivity : AppCompatActivity() {
    private val visibleThreshold = 16
    private var host = "127.0.0.1"
    private var port = 80
    private var pwd = "demo"
    private var spwd: String? = null
    private var dict: SimpleDict? = null
    private var cm: ClipboardManager? = null
    private var mViewPagerPosition = 0
    private val mControlBarStates = arrayOf(ControlBarState(visibleThreshold+8), ControlBarState(visibleThreshold+8))
    private val mVPAdapter get() = fmvp.adapter as MainFragment.PagerAdapter

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        getSharedPreferences("remote", MODE_PRIVATE)?.apply {
            if(contains("host")) getString("host", host)?.apply { host = this }
            if(contains("port")) getInt("port", port).apply { port = this }
            if(contains("pwd")) getString("pwd", pwd)?.apply { pwd = this }
            if(contains("spwd")) getString("spwd", spwd)?.apply { spwd = this }
        }
        dict = SimpleDict(Client(host, port), pwd, externalCacheDir, spwd)

        cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        if(savedInstanceState == null) {
            MainFragment.handleOnViewCreated = HandleOnViewCreated()
            InnerFragment.handleOnCreateView = HandleOnCreateView()
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add(R.id.fffc, MainFragment())
            }
        }

        ffsw.apply {
            setOnRefreshListener {
                fetchThread {
                    updateSize()
                }
            }
            isRefreshing = true
            fetchThread {
                updateSize()
            }
        }

        ffms.apply {
            val recyclerView = findViewById<RecyclerView>(com.lapism.search.R.id.search_recycler_view)
            val lm = LinearLayoutManager(this@MainActivity)
            setAdapterLayoutManager(lm)
            val adapter = SearchViewHolder(recyclerView).RecyclerViewAdapter()
            recyclerView.addOnScrollListener(object: RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    val a = lm.findFirstVisibleItemPosition()
                    val b = lm.findLastVisibleItemPosition()
                    val total = lm.itemCount
                    if(a <= 0) adapter.scrollUp(1)
                    else if(b >= total-1) adapter.scrollDown(1)
                }
            })
            setAdapter(adapter)
            navigationIconSupport = SearchLayout.NavigationIconSupport.SEARCH
            setMicIconImageResource(R.drawable.ic_setting)
            val micView = findViewById<ImageButton>(com.lapism.search.R.id.search_image_view_mic)
            setClearFocusOnBackPressed(true)
            setOnNavigationClickListener(object : SearchLayout.OnNavigationClickListener {
                override fun onNavigationClick(hasFocus: Boolean) {
                    if (hasFocus()) clearFocus()
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
                        val key = query.toString()
                        val data = dict?.get(key)
                        showDictAlert(key, data, recyclerView.children.toList().let { children ->
                            val i = children.map { it.ta.text }.indexOf(key)
                            if(i >= 0) children[i] else null
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
                    navigationIconSupport = if (hasFocus) {
                        hideControlCard(true)
                        SearchLayout.NavigationIconSupport.ARROW
                    }
                    else {
                        micView.postDelayed({
                            micView.visibility = View.VISIBLE
                            showControlCard(true)
                        }, 233)
                        SearchLayout.NavigationIconSupport.SEARCH
                    }
                }
            })
        }

        var isSeeking = false
        cctrl.sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, isUser: Boolean) {
                Log.d("MyMain", "seek to $p")
                if(isSeeking) {
                    val bar = mControlBarStates[mViewPagerPosition]
                    bar.index = bar.getPosition(p)
                    updateSize(false)
                }
            }

            override fun onStartTrackingTouch(s: SeekBar?) {
                isSeeking = true
                Log.d("MyMain", "onStartTrackingTouch")
            }

            override fun onStopTrackingTouch(s: SeekBar?) {
                isSeeking = false
                Log.d("MyMain", "onStopTrackingTouch")
                s?.progress?.let {
                    val ad = mVPAdapter.views[mViewPagerPosition]?.recyclerView?.adapter as? ListViewHolder.RecyclerViewAdapter ?: return
                    ad.setProgress(it)
                }
            }
        })

        var isHide = false
        cbcard.setOnClickListener {
            Log.d("MyMain", "cbcard clicked")
            isHide = if (isHide) {
                showControlCard()
                false
            } else {
                hideControlCard()
                true
            }
        }

        cbcard.setOnLongClickListener {
            if(!isHide) AlertDialog.Builder(this)
                .setTitle(R.string.alert_select_sort_type)
                .setIcon(R.mipmap.ic_launcher)
                .setSingleChoiceItems(R.array.sort_type, mControlBarStates[mViewPagerPosition].sort) { d, p ->
                    mControlBarStates[mViewPagerPosition].sort = p
                    d.cancel()
                    val ad = mVPAdapter.views[mViewPagerPosition]?.recyclerView?.adapter as? ListViewHolder.RecyclerViewAdapter ?: return@setSingleChoiceItems
                    ad.refresh()
                }.show()
            true
        }
    }

    private fun updateSize(updateSeekbar: Boolean = true) = runOnUiThread {
        Log.d("MyMain", "update size, updateSeekbar: $updateSeekbar")
        val bar = mControlBarStates[mViewPagerPosition]
        cctrl?.lbtindex?.text = bar.formatRange(getString(R.string.info_index_meter))
        cctrl?.lbttotal?.text = bar.formatSize(getString(R.string.info_words_total))
        if (updateSeekbar) cctrl?.sb?.progress = bar.getPercentage()
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
                    (mVPAdapter.views[mViewPagerPosition]?.recyclerView?.adapter as? ListViewHolder.RecyclerViewAdapter)?.refresh()
                    updateSize()
                    doWhenFinish?.apply { this() }
                }
            }
        }.start()
    }

    private fun showDictAlert(key: String, data: String?, line: View?) {
        val hintAdd = if(data != null && data != "null") "重设" else "添加"
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
                                    val k = key.trim().replace(Regex("[\\uFF00-\\uFF5E]")) { (it.value[0] - 0xFEE0).toString() }
                                    if(dict?.set(k, newText) == true) {
                                        line?.tb?.text = newText
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
                        }
                        else runOnUiThread {
                            Toast.makeText(this, "失败", Toast.LENGTH_SHORT).show()
                        }
                    }.start()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .show()
    }

    private fun showControlCard(completely: Boolean = false){
        cctrl.sb.isEnabled = true
        if(completely) {
            cbcard.alpha = 0f
            cbcard.visibility = View.VISIBLE
            ObjectAnimator.ofFloat(cbcard, "alpha", 0f, 0.9f).setDuration(233).start()
            return
        }
        ObjectAnimator.ofFloat(cbcard, "alpha", 0.3f, 0.9f).setDuration(233).start()
        ObjectAnimator.ofFloat(cbcard, "translationX", cbcard.width.toFloat() * 0.9f, 0f).setDuration(233).start()
    }

    private fun hideControlCard(completely: Boolean = false){
        cctrl.sb.isEnabled = false
        if(completely) {
            cbcard.visibility = View.GONE
            return
        }
        ObjectAnimator.ofFloat(cbcard, "alpha", 0.9f, 0.3f).setDuration(233).start()
        ObjectAnimator.ofFloat(cbcard, "translationX", 0f, cbcard.width.toFloat() * 0.9f).setDuration(233).start()
    }

    inner class SearchViewHolder(itemView: View) : ListViewHolder(itemView) {
        inner class RecyclerViewAdapter : ListViewHolder.RecyclerViewAdapter(visibleThreshold) {
            override fun getKeys(filterText: CharSequence?) = filterText?.let { filter(it) }
            override fun getValue(key: String) = dict?.get(key)
            private fun filter(text: CharSequence): List<String> {
                return dict?.keys?.filter {
                        it.contains(text, true)
                }?.toSet()?.plus(
                    dict?.filterValues {
                        it?.contains(text, true) ?: false
                    }.let {
                        val newSet = mutableSetOf<String>()
                        it?.keys?.forEach { k ->
                            newSet += k
                        }
                        newSet
                    }
                )?.toList()?: emptyList()
            }
        }
    }

    inner class LikeViewHolder(itemView: View, private val onlyLike: Boolean) : ListViewHolder(itemView) {
        inner class RecyclerViewAdapter: ListViewHolder.RecyclerViewAdapter(visibleThreshold+8) {
            override fun getKeys(filterText: CharSequence?) = (
                if(onlyLike) dictPreferences?.all?.keys?.let { keys ->
                    Log.d("MyMain", "LikeViewHolder getKeys like")
                    mControlBarStates[1].let { bar ->
                        bar.total = keys.size
                        bar.sort(keys.toList())
                    }
                }
                else dict?.latestKeys?.let { keys ->
                    Log.d("MyMain", "LikeViewHolder getKeys all, set size: ${keys.size}")
                    mControlBarStates[0].let { bar ->
                        bar.total = keys.size
                        bar.sort(keys.toList())
                    }
                }
            )?: emptyList()
            override fun getValue(key: String) = dict?.get(key)?:dictPreferences?.getString(key, "null")?:"N/A"
        }
    }

    open inner class ListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val recyclerView: RecyclerView? = itemView as? RecyclerView
        open inner class RecyclerViewAdapter(private val renderLinesCount: Int) :
            RecyclerView.Adapter<ListViewHolder>() {
            private var listKeys: List<String>? = null
            private var index = 0
            val dictPreferences: SharedPreferences? = getSharedPreferences("dict", MODE_PRIVATE)
            var hasRefreshed = false
            open fun getKeys(filterText: CharSequence? = null): List<String>? = null
            open fun getValue(key: String): String? = null

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {
                return ListViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.line_word, parent, false)
                )
            }

            @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
            override fun onBindViewHolder(holder: ListViewHolder, p: Int) {
                val position = p + index
                Log.d("MyMain", "Bind open at $p($position)")
                Thread{
                    listKeys?.apply {
                        if (position >= size) return@Thread
                        val key = get(position)
                        val data = getValue(key)
                        val like = dictPreferences?.contains(key) == true
                        //Log.d("MyMain", "Like status of $key is $like")
                        holder.itemView.apply {
                            runOnUiThread {
                                ta.visibility = View.VISIBLE
                                tn.text = key
                                ta.text = key
                                tb.text = data
                                vl.setBackgroundResource(if(like) R.drawable.ic_like_filled else R.drawable.ic_like)
                                //Log.d("MyMain", "Set like of $key: $like")
                                setOnClickListener {
                                    showDictAlert(key, data, this)
                                }
                                setOnLongClickListener {
                                    cm?.setPrimaryClip(ClipData.newPlainText("SimpleDict", "$key\n$data"))
                                    runOnUiThread {
                                        Toast.makeText(this@MainActivity, R.string.toast_copied, Toast.LENGTH_SHORT).show()
                                    }
                                    true
                                }
                                vl.setOnClickListener {
                                    dictPreferences?.apply {
                                        if(contains(key)) {
                                            edit { remove(key) }
                                            it.setBackgroundResource(R.drawable.ic_like)
                                            Log.d("MyMain", "unliked $key")
                                        } else {
                                            edit { putString(key, data) }
                                            it.setBackgroundResource(R.drawable.ic_like_filled)
                                            Log.d("MyMain", "liked $key")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if(recyclerView?.isComputingLayout == false) {
                        if(p >= itemCount-1) scrollDown(if(p < renderLinesCount) 4 else 1)
                        else if(p <= 1) scrollUp(if(p < renderLinesCount) 4 else 1)
                    }
                }.start()
            }

            override fun getItemCount() = (listKeys?.size?:0).let { if(it > renderLinesCount) renderLinesCount else it }

            @SuppressLint("NotifyDataSetChanged")
            fun refresh(filterText: CharSequence? = null) {
                index = 0
                listKeys = getKeys(filterText)
                notifyDataSetChanged()
                hasRefreshed = true
            }

            @SuppressLint("NotifyDataSetChanged")
            fun scrollDown(n: Int) {
                if((listKeys?.size ?: 0) <= renderLinesCount) return
                val oldIndex = index
                val nextIndex = if(oldIndex + n + renderLinesCount > (listKeys?.size ?: 0)) (listKeys?.size ?: 0) - renderLinesCount else oldIndex + n
                if (oldIndex == nextIndex) return
                if(nextIndex < 0) return
                index = nextIndex
                if(n >= renderLinesCount) {
                    runOnUiThread { notifyDataSetChanged() }
                    return
                }
                // index         next index
                // +*************************
                //               +*************************
                //               ---remain---       ↑
                // ----delete----  →  →  →  →  →  ↗
                val insert = nextIndex - oldIndex
                runOnUiThread {
                    notifyItemRangeInserted(renderLinesCount, insert)
                    notifyItemRangeRemoved(0, insert)
                }
            }

            @SuppressLint("NotifyDataSetChanged")
            fun scrollUp(n: Int) {
                if((listKeys?.size ?: 0) <= renderLinesCount) return
                val oldIndex = index
                val nextIndex = if(oldIndex-n >= 0) oldIndex-n else 0
                if(oldIndex == nextIndex) return
                index = nextIndex
                if(n >= renderLinesCount) {
                    runOnUiThread { notifyDataSetChanged() }
                    return
                }
                val insert = oldIndex - nextIndex
                runOnUiThread {
                    notifyItemRangeInserted(0, insert)
                    notifyItemRangeRemoved(renderLinesCount, insert)
                }
            }

            fun getPosition() = index

            @SuppressLint("NotifyDataSetChanged")
            fun setProgress(p: Int) {
                if(p > 100 || p < 0) return
                var newIndex = p * (listKeys?.size?:0) / 100
                if(newIndex + renderLinesCount > (listKeys?.size?:0)) {
                    newIndex = (listKeys?.size?:0) - renderLinesCount
                    if(newIndex < 0) newIndex = 0
                }
                val oldIndex = index
                if (oldIndex == newIndex) return
                val n = newIndex - oldIndex
                if(n >= renderLinesCount || n <= -renderLinesCount) {
                    index = newIndex
                    runOnUiThread { notifyDataSetChanged() }
                    return
                }
                if(n > 0) scrollDown(n)
                else scrollUp(-n)
            }
        }
    }

    inner class HandleOnViewCreated: MainFragment.HandleOnViewCreated {
        override fun onPageSelected(position: Int) {
            mViewPagerPosition = position
            val ad = mVPAdapter.views[mViewPagerPosition]?.recyclerView?.adapter as? ListViewHolder.RecyclerViewAdapter
            if(ad?.hasRefreshed == false) {
                ad.refresh()
            }
            updateSize()
        }

        override fun onPageScrollStateChanged(state: Int) {
            val ad = mVPAdapter.views[mViewPagerPosition]?.recyclerView?.adapter as? ListViewHolder.RecyclerViewAdapter
            this@MainActivity.ffsw.isEnabled = state == ViewPager.SCROLL_STATE_IDLE && ad?.getPosition() == 0
            Log.d("MyMain", "set ffsw enabled: ${this@MainActivity.ffsw.isEnabled}")
        }

    }

    inner class HandleOnCreateView: InnerFragment.HandleOnCreateView {
        override fun onCreateView(
            p: Int,
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val r = RecyclerView(inflater.context)
            r.layoutParams = ViewGroup.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            val ad = LikeViewHolder(r, p == 1).RecyclerViewAdapter()
            r.apply {
                val lm = LinearLayoutManager(this@MainActivity)
                layoutManager = lm
                adapter = ad
                addOnScrollListener(object: RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)
                        val newStart = ad.getPosition()
                        val bar = mControlBarStates[p]
                        Log.d("MyMain", "new start: $newStart, index: ${bar.index}, sy: ${recyclerView?.scrollY}")
                        if (newStart != bar.index) {
                            bar.index = newStart
                            updateSize()
                        }
                    }
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        super.onScrollStateChanged(recyclerView, newState)
                        val a = lm.findFirstVisibleItemPosition()
                        val b = lm.findLastVisibleItemPosition()
                        Log.d("MyMain", "new scroll state: $newState, a: $a, b: $b")
                        this@MainActivity.ffsw.isEnabled = newState == 0 && a == 0
                        val total = lm.itemCount
                        if(a <= 0) ad.scrollUp(1)
                        else if(b >= total-1) ad.scrollDown(1)
                    }
                })
            }
            return r
        }
    }
}
