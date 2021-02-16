package top.fumiama.simpledict

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.lapism.search.internal.SearchLayout
import com.lapism.search.util.SearchUtils
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.line_word.view.*
import java.lang.Thread.sleep

class MainActivity : AppCompatActivity() {
    private var keys = arrayOf<String>()
    private var datas = arrayOf<String?>()
    private val dict = SimpleDict(Client("192.168.98.2", 8000))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ffms.apply {
            setAdapterLayoutManager(LinearLayoutManager(this@MainActivity))
            val adapter = ViewData(findViewById(R.id.search_recycler_view)).RecyclerViewAdapter()
            setAdapter(adapter)
            navigationIconSupport = SearchLayout.NavigationIconSupport.SEARCH
            setOnNavigationClickListener(object : SearchLayout.OnNavigationClickListener {
                override fun onNavigationClick(hasFocus: Boolean) {
                    if (hasFocus()) clearFocus()
                    else requestFocus()
                }
            })
            setTextHint(android.R.string.search_go)
            setOnQueryTextListener(object : SearchLayout.OnQueryTextListener {
                val sysTime get() = System.currentTimeMillis() / 1000
                var lastVisitTime = sysTime
                val isLast get() = sysTime - lastVisitTime > 1
                var hasLoad = true
                var key: CharSequence = ""
                    set(value) {
                        field = value
                        lastVisitTime = sysTime
                        hasLoad = false
                    }

                init {
                    Thread {
                        while (true) {
                            sleep(1)
                            if (isLast && !hasLoad) {
                                adapter.filter(key)
                                hasLoad = true
                            }
                        }
                    }.start()
                }

                override fun onQueryTextChange(newText: CharSequence): Boolean {
                    if (newText.isNotEmpty()) key = newText
                    return true
                }

                override fun onQueryTextSubmit(query: CharSequence): Boolean {
                    if(query.isNotEmpty()) Thread{
                        val data = dict[query]
                        runOnUiThread {
                            showDictAlert(query.toString(), data)
                        }
                    }.start()
                    return true
                }
            })
            setOnMicClickListener(object : SearchLayout.OnMicClickListener {
                override fun onMicClick() {
                    if (SearchUtils.isVoiceSearchAvailable(this@MainActivity)) {
                        SearchUtils.setVoiceSearch(this@MainActivity, "speak")
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
        if(ffms.hasFocus()) ffms.clearFocus()
        else super.onBackPressed()
    }

    private fun showDictAlert(key: String, data: String?) {
        AlertDialog.Builder(this@MainActivity)
                .setTitle(key)
                .setMessage(data)
                .setPositiveButton(if(data != "null") "重设" else "添加") { _, _ ->
                    val t = EditText(this@MainActivity)
                    AlertDialog.Builder(this@MainActivity)
                            .setTitle("重设$key")
                            .setView(t)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                if (t.text.isNotEmpty()) Thread {
                                    dict[key] = t.text.toString()
                                }.start()
                            }
                            .setNegativeButton(android.R.string.cancel) { _, _ -> }
                            .show()
                }
                .setNeutralButton("收藏") { _, _ ->
                    getSharedPreferences("dict", MODE_PRIVATE)?.edit()?.let {
                        it.putString(key, data)
                        it.apply()
                    }
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .show()
    }

    inner class ViewData(itemView: View) : RecyclerView.ViewHolder(itemView) {
        inner class RecyclerViewAdapter :
                RecyclerView.Adapter<ViewData>() {
            var count = 0
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewData {
                return ViewData(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.line_word, parent, false)
                )
            }

            @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
            override fun onBindViewHolder(holder: ViewData, position: Int) {
                Log.d("MyMain", "Bind $position")
                if(position < keys.size) {
                    holder.itemView.ta.text = keys[position]
                    if(position < datas.size) holder.itemView.tb.text = datas[position]
                    holder.itemView.setOnClickListener {
                        showDictAlert(keys[position], if(position < datas.size) datas[position] else "null")
                    }
                }
            }

            override fun getItemCount() = count

            fun filter(text: CharSequence) {
                dict.pattern = text
                dict.keys.let {
                    count = it.size
                    if (count > 0) {
                        keys = arrayOf()
                        datas = arrayOf()
                        it.forEach {
                            keys += it
                            datas += dict[it]
                            Log.d("MyMain", "Get key: $it is ${datas.last()}")
                        }
                    }
                }
                runOnUiThread { notifyDataSetChanged() }
            }
        }
    }
}