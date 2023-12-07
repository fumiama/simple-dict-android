package top.fumiama.simpledict

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView

class InnerFragment(private val mHandleOnCreateView: HandleOnCreateView) : Fragment() {
    var recyclerView: RecyclerView? = null
    private val p get() = arguments?.getInt("p", 0)?:0

    constructor(): this(handleOnCreateView!!)

    interface HandleOnCreateView {
        fun onCreateView(p: Int, inflater: LayoutInflater,
                         container: ViewGroup?,
                         savedInstanceState: Bundle?): View
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        recyclerView = mHandleOnCreateView.onCreateView(p, inflater, container, savedInstanceState) as RecyclerView
        return recyclerView!!
    }

    companion object {
        var handleOnCreateView: HandleOnCreateView? = null
    }
}
