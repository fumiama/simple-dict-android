package top.fumiama.simpledict

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import kotlinx.android.synthetic.main.fragment_main.fmtab
import kotlinx.android.synthetic.main.fragment_main.fmvp

class MainFragment(private val mHandleOnViewCreated: HandleOnViewCreated, private val mHandleOnCreateView: InnerFragment.HandleOnCreateView): Fragment() {
    constructor() : this(handleOnViewCreated!!, InnerFragment.handleOnCreateView!!)

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    interface HandleOnViewCreated {
        fun onPageSelected(position: Int)
        fun onPageScrollStateChanged(state: Int)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fmvp.adapter = PagerAdapter(childFragmentManager)
        fmvp.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) { }

            override fun onPageSelected(position: Int) {
                Log.d("MyMF", "select page: $position")
                mHandleOnViewCreated.onPageSelected(position)
            }

            override fun onPageScrollStateChanged(state: Int) {
                Log.d("MyMF", "scroll state: $state, idle: ${ViewPager.SCROLL_STATE_IDLE}")
                mHandleOnViewCreated.onPageScrollStateChanged(state)
            }
        })
        fmtab.setupWithViewPager(fmvp)
    }

    inner class PagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {
        var views = arrayOf<InnerFragment?>(null, null)
        override fun getCount(): Int = 2

        override fun getItem(i: Int): Fragment {
            if(views[i] != null) return views[i]!!
            val f = InnerFragment(mHandleOnCreateView)
            val b = Bundle()
            b.putInt("p", i)
            f.arguments = b
            views[i] = f
            return f
        }

        override fun getPageTitle(position: Int): CharSequence {
            return getString(if(position == 0) R.string.tab_all_words else R.string.tab_liked_words)
        }
    }

    companion object {
        var handleOnViewCreated: HandleOnViewCreated? = null
    }
}