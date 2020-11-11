package com.hoc081098.paginationmviflow.ui.main

import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hoc081098.paginationmviflow.R
import com.hoc081098.paginationmviflow.isOrientationPortrait
import com.hoc081098.paginationmviflow.toast
import com.hoc081098.paginationmviflow.ui.main.MainContract.ViewIntent
import com.jakewharton.rxbinding3.recyclerview.scrollEvents
import com.jakewharton.rxbinding3.swiperefreshlayout.refreshes
import dagger.android.support.AndroidSupportInjection
import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.fragment_main.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.LazyThreadSafetyMode.NONE

@ExperimentalCoroutinesApi
class MainFragment : Fragment() {
  @Inject
  lateinit var factory: ViewModelProvider.Factory
  private val mainVM by viewModels<MainVM> { factory }
  private val compositeDisposable = CompositeDisposable()

  private val maxSpanCount get() = if (requireContext().isOrientationPortrait) 2 else 4
  private val visibleThreshold get() = 2 * maxSpanCount + 1

  private val adapter by lazy(NONE) { MainAdapter(compositeDisposable, recycler.recycledViewPool) }

  override fun onCreate(savedInstanceState: Bundle?) {
    AndroidSupportInjection.inject(this)
    super.onCreate(savedInstanceState)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View = inflater.inflate(R.layout.fragment_main, container, false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setupView()
    bindVM()
  }

  private fun setupView() {
    recycler.run {
      setHasFixedSize(true)
      adapter = this@MainFragment.adapter

      layoutManager = GridLayoutManager(context, maxSpanCount).apply {
        spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
          override fun getSpanSize(position: Int): Int {
            return if (adapter!!.getItemViewType(position) == R.layout.recycler_item_photo) {
              1
            } else {
              maxSpanCount
            }
          }
        }
      }

      val space = 8
      addItemDecoration(object : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
          outRect: Rect,
          view: View,
          parent: RecyclerView,
          state: RecyclerView.State
        ) {
          val adapter = parent.adapter!!
          val position = parent.getChildAdapterPosition(view)

          when (adapter.getItemViewType(position)) {
            R.layout.recycler_item_horizontal_list -> {
              outRect.left = space
              outRect.right = space
              outRect.top = space
              outRect.bottom = 0
            }
            R.layout.recycler_item_photo -> {
              outRect.top = space
              outRect.bottom = 0

              val column = (position - 1) % maxSpanCount
              outRect.right = space * (column + 1) / maxSpanCount
              outRect.left = space - space * column / maxSpanCount
            }
            R.layout.recycler_item_placeholder -> {
              outRect.left = space
              outRect.right = space
              outRect.top = space
              outRect.bottom = space
            }
          }
        }
      })

      addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
          if (e.action == MotionEvent.ACTION_DOWN &&
            rv.scrollState == RecyclerView.SCROLL_STATE_SETTLING
          ) {
            rv.stopScroll()
          }
          return false
        }
      })
    }
  }

  private fun bindVM() {
    mainVM.stateD.observe(viewLifecycleOwner, Observer { vs ->
      vs ?: return@Observer
      Log.d("###", "${vs.isRefreshing} ${vs.enableRefresh}")

      adapter.submitList(vs.items)

      if (vs.isRefreshing) {
        swipe_refresh.post { swipe_refresh.isRefreshing = true }
      } else {
        swipe_refresh.isRefreshing = false
      }
      swipe_refresh.isEnabled = vs.enableRefresh
    })
    mainVM
      .singleEventObservable
      .subscribeBy(onNext = ::handleSingleEvent)
      .addTo(compositeDisposable)

    mainVM.processIntents(
      Observable.mergeArray(
        Observable.just(ViewIntent.Initial),
        loadNextPageIntent(),
        swipe_refresh.refreshes().map { ViewIntent.Refresh },
        adapter
          .retryObservable
          .throttleFirst(500, TimeUnit.MILLISECONDS)
          .map { ViewIntent.RetryLoadPage },
        adapter
          .loadNextPageHorizontalObservable
          .map { ViewIntent.LoadNextPageHorizontal },
        adapter
          .retryNextPageHorizontalObservable
          .throttleFirst(500, TimeUnit.MILLISECONDS)
          .map { ViewIntent.RetryLoadPageHorizontal },
        adapter
          .retryHorizontalObservable
          .throttleFirst(500, TimeUnit.MILLISECONDS)
          .map { ViewIntent.RetryHorizontal }
      )
    ).addTo(compositeDisposable)
  }

  private fun handleSingleEvent(event: MainContract.SingleEvent) {
    return when (event) {
      MainContract.SingleEvent.RefreshSuccess -> {
        toast("Refresh success")
        adapter.scrollHorizontalListToFirst()
      }
      is MainContract.SingleEvent.RefreshFailure -> {
        toast(
          "Refresh failure: ${event.error.message ?: ""}"
        )
      }
      is MainContract.SingleEvent.GetPostsFailure -> {
        toast(
          "Get posts failure: ${event.error.message ?: ""}"
        )
      }
      MainContract.SingleEvent.HasReachedMaxHorizontal -> {
        toast("Got all posts")
      }
      is MainContract.SingleEvent.GetPhotosFailure -> {
        toast(
          "Get photos failure: ${event.error.message ?: ""}"
        )
      }
      MainContract.SingleEvent.HasReachedMax -> {
        toast("Got all photos")
      }
    }
  }

  private fun loadNextPageIntent(): ObservableSource<ViewIntent> {
    return recycler
      .scrollEvents()
      .filter { (_, _, dy) ->
        val layoutManager = recycler.layoutManager as GridLayoutManager
        dy > 0 && layoutManager.findLastVisibleItemPosition() + visibleThreshold >= layoutManager.itemCount
      }
      .map { ViewIntent.LoadNextPage }
  }

  override fun onDestroyView() {
    super.onDestroyView()

    compositeDisposable.clear()
    recycler.adapter = null
  }
}