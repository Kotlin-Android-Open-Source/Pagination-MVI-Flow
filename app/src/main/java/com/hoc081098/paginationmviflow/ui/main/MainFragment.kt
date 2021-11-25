package com.hoc081098.paginationmviflow.ui.main

import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hoc081098.paginationmviflow.R
import com.hoc081098.paginationmviflow.collectInViewLifecycle
import com.hoc081098.paginationmviflow.databinding.FragmentMainBinding
import com.hoc081098.paginationmviflow.isOrientationPortrait
import com.hoc081098.paginationmviflow.refreshes
import com.hoc081098.paginationmviflow.scrollEvents
import com.hoc081098.paginationmviflow.toast
import com.hoc081098.paginationmviflow.ui.main.MainContract.ViewIntent
import com.hoc081098.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlin.LazyThreadSafetyMode.NONE

@AndroidEntryPoint
@OptIn(ExperimentalCoroutinesApi::class)
class MainFragment : Fragment(R.layout.fragment_main) {
  private val mainVM by viewModels<MainVM>()
  private val binding by viewBinding<FragmentMainBinding> {
    recycler.adapter = null
  }

  private inline val maxSpanCount get() = if (requireContext().isOrientationPortrait) 2 else 4
  private inline val visibleThreshold get() = 2 * maxSpanCount + 1

  private val adapter by lazy(NONE) {
    MainAdapter(
      viewLifecycleOwner.lifecycleScope,
      binding.recycler.recycledViewPool
    )
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setupView()
    bindVM()
  }

  private fun setupView() {
    binding.recycler.run {
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
    val swipeRefresh = binding.swipeRefresh

    mainVM.stateFlow.collectInViewLifecycle(this) { vs ->
      Log.d("###", "${vs.isRefreshing} ${vs.enableRefresh}")

      adapter.submitList(vs.items)

      if (vs.isRefreshing) {
        swipeRefresh.post { swipeRefresh.isRefreshing = true }
      } else {
        swipeRefresh.isRefreshing = false
      }
      swipeRefresh.isEnabled = vs.enableRefresh
    }

    mainVM
      .singleEventFlow
      .collectInViewLifecycle(this) { handleSingleEvent(it) }

    merge(
      flowOf(ViewIntent.Initial),
      loadNextPageIntent(),
      binding.swipeRefresh.refreshes().map { ViewIntent.Refresh },
      adapter
        .retryFlow
        // TODO: https://github.com/Kotlin/kotlinx.coroutines/pull/2128#issuecomment-655944187
        // .throttleFirst(500, TimeUnit.MILLISECONDS)
        .map { ViewIntent.RetryLoadPage },
      adapter
        .loadNextPageHorizontalFlow
        .map { ViewIntent.LoadNextPageHorizontal },
      adapter
        .retryNextPageHorizontalFlow
        // TODO: https://github.com/Kotlin/kotlinx.coroutines/pull/2128#issuecomment-655944187
        // .throttleFirst(500, TimeUnit.MILLISECONDS)
        .map { ViewIntent.RetryLoadPageHorizontal },
      adapter
        .retryHorizontalFlow
        // TODO: https://github.com/Kotlin/kotlinx.coroutines/pull/2128#issuecomment-655944187
        // .throttleFirst(500, TimeUnit.MILLISECONDS)
        .map { ViewIntent.RetryHorizontal },
    )
      .onEach { mainVM.processIntent(it) }
      .launchIn(viewLifecycleOwner.lifecycleScope)
  }

  private suspend fun handleSingleEvent(event: MainContract.SingleEvent) {
    return when (event) {
      MainContract.SingleEvent.RefreshSuccess -> {
        toast("Refresh success")
        adapter.scrollHorizontalListToFirst()
      }
      is MainContract.SingleEvent.RefreshFailure -> {
        toast("Refresh failure: ${event.error.message ?: ""}")
      }
      is MainContract.SingleEvent.GetPostsFailure -> {
        toast("Get posts failure: ${event.error.message ?: ""}")
      }
      MainContract.SingleEvent.HasReachedMaxHorizontal -> {
        toast("Got all posts")
      }
      is MainContract.SingleEvent.GetPhotosFailure -> {
        toast("Get photos failure: ${event.error.message ?: ""}")
      }
      MainContract.SingleEvent.HasReachedMax -> {
        toast("Got all photos")
      }
    }
  }

  private fun loadNextPageIntent(): Flow<ViewIntent.LoadNextPage> {
    return binding.recycler
      .scrollEvents()
      .filter { (_, _, dy) ->
        val layoutManager = binding.recycler.layoutManager as GridLayoutManager
        dy > 0 && layoutManager.findLastVisibleItemPosition() + visibleThreshold >= layoutManager.itemCount
      }
      .map { ViewIntent.LoadNextPage }
  }
}
