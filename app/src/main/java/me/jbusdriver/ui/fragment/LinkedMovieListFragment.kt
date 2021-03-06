package me.jbusdriver.ui.fragment

import android.graphics.Paint
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import com.bumptech.glide.request.target.DrawableImageViewTarget
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import jbusdriver.me.jbusdriver.R
import kotlinx.android.synthetic.main.layout_actress_attr.view.*
import kotlinx.android.synthetic.main.layout_load_all.view.*
import me.jbusdriver.common.*
import me.jbusdriver.mvp.LinkListContract
import me.jbusdriver.mvp.bean.*
import me.jbusdriver.mvp.model.CollectModel
import me.jbusdriver.mvp.presenter.LinkAbsPresenterImpl
import me.jbusdriver.mvp.presenter.MovieLinkPresenterImpl
import me.jbusdriver.ui.activity.SearchResultActivity


/**
 * ilink 由跳转链接进入的 /历史记录
 */
class LinkedMovieListFragment : AbsMovieListFragment(), LinkListContract.LinkListView {
    private val link by lazy {
        val link = arguments?.getSerializable(C.BundleKey.Key_1)  as? ILink
                ?: error("no link data ")
        KLog.i("link data : $link")
        link
    }

    private val isSearch by lazy { link is SearchLink && activity != null && activity is SearchResultActivity }
    private val isHistory by lazy { arguments?.getBoolean(C.BundleKey.Key_2, false) ?: false }


    private var collectMenu: MenuItem? = null
    private var removeCollectMenu: MenuItem? = null
    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        val isCollect by lazy {
            CollectModel.has(link.convertDBItem())
        }
        if (!isHistory || link !is PageLink) { //历史记录隐藏
            collectMenu = menu?.add(Menu.NONE, R.id.action_add_movie_collect, 10, "收藏")?.apply {
                setIcon(R.drawable.ic_star_border_white_24dp)
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                isVisible = !isCollect
            }
            removeCollectMenu = menu?.add(Menu.NONE, R.id.action_remove_movie_collect, 10, "取消收藏")?.apply {
                setIcon(R.drawable.ic_star_white_24dp)
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                isVisible = isCollect
            }
        }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when (id) {
            R.id.action_add_movie_collect -> {
                //收藏
                KLog.d("收藏")
                val res = CollectModel.addToCollect(link.convertDBItem())
                if (res) {
                    collectMenu?.isVisible = false
                    removeCollectMenu?.isVisible = true
                }
            }
            R.id.action_remove_movie_collect -> {
                //取消收藏
                KLog.d("取消收藏")
                val res = CollectModel.removeCollect(link.convertDBItem())
                if (res) {
                    collectMenu?.isVisible = true
                    removeCollectMenu?.isVisible = false
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }


    override fun initData() {
        if (isSearch) {
            RxBus.toFlowable(SearchWord::class.java).subscribeBy { sea ->
                (mBasePresenter as? LinkAbsPresenterImpl<*>)?.let {
                    (it.linkData as SearchLink).query = sea.query
                    it.onRefresh()
                }
            }.addTo(rxManager)
        }
    }

    override fun gotoSearchResult(query: String) {
        (mBasePresenter as?  LinkAbsPresenterImpl<*>)?.let {
            if (isSearch) {
//                it.linkData.query = query
//                it.onRefresh()
                viewContext.toast("新搜索 : $query")
                RxBus.post(SearchWord(query))
            } else {
                super.gotoSearchResult(query)
            }
        }
    }

    override fun createPresenter() = MovieLinkPresenterImpl(link, arguments?.getBoolean(LinkableListFragment.MENU_SHOW_ALL, false)
            ?: false, isHistory)

    override fun <T> showContent(data: T?) {
        if (data is String) {
            //  getLoadAllView(data)?.let { attrViews.put(data,it) }
            tempSaveBundle.putString("temp:load:all", data)
        }

        if (data is IAttr) {
            //attrViews.add(getMovieAttrView(data))
            tempSaveBundle.putSerializable("temp:IAttr", data)
        }
    }

    override fun showContents(data: List<*>) {
        adapter.removeAllHeaderView()
        KLog.d("tempSaveBundle : $tempSaveBundle")
        //load all
        tempSaveBundle.getString("temp:load:all")?.let {
            getLoadAllView(it)?.let { adapter.addHeaderView(it) }
        }

        // movie attr
        (tempSaveBundle.getSerializable("temp:IAttr") as? IAttr)?.let {
            adapter.addHeaderView(getMovieAttrView(it))
        }
        KLog.d("tempSaveBundle add : ${data.size}")
        super.showContents(data)

    }

    private fun getMovieAttrView(data: IAttr): View = when (data) {
        is ActressAttrs -> {
            this.viewContext.inflate(R.layout.layout_actress_attr).apply {
                //img
                GlideApp.with(this@LinkedMovieListFragment).load(data.imageUrl.toGlideUrl).into(DrawableImageViewTarget(this.iv_actress_avatar))
                //title
                this.ll_attr_container.addView(generateTextView().apply {
                    textSize = 16f
                    setTextColor(R.color.primaryText.toColorInt())
                    text = data.title
                })

                data.info.forEach {
                    this.ll_attr_container.addView(generateTextView().apply { text = it })
                }
            }
        }
        else -> error("current not provide for IAttr $data")
    }

    private fun getLoadAllView(data: String): View? {
        return data.split("：").let { txts ->
            if (txts.size == 2) {
                this.viewContext.inflate(R.layout.layout_load_all).apply {
                    tv_info_title.text = txts[0]
                    val spans = txts[1].split("，")
                    require(spans.size == 2)
                    tv_change_a.text = spans[0]
                    tv_change_b.text = spans[1]
                    tv_change_b.paintFlags = tv_change_b.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                    tv_change_b.setOnClickListener {
                        val showAll = tempSaveBundle.getBoolean(MENU_SHOW_ALL)
                        mBasePresenter?.setAll(!showAll)
                        mBasePresenter?.loadData4Page(1)
                        tempSaveBundle.putBoolean(MENU_SHOW_ALL, !showAll)
                    }
                }
            } else null
        }

    }


    private fun generateTextView() = TextView(this.viewContext).apply {
        textSize = 11.5f
        setTextColor(R.color.secondText.toColorInt())
    }


    /*================================================*/

    companion
    object {
        //电影列表,演员,链接,搜索入口
        fun newInstance(link: ILink) = LinkedMovieListFragment().apply {
            arguments = Bundle().apply {
                putSerializable(C.BundleKey.Key_1, link)
            }
        }
    }
    /*================================================*/
}