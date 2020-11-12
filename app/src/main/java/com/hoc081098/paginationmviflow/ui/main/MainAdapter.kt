package com.hoc081098.paginationmviflow.ui.main

import android.graphics.Rect
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.hoc081098.paginationmviflow.*
import com.hoc081098.paginationmviflow.databinding.RecyclerItemHorizontalListBinding
import com.hoc081098.paginationmviflow.databinding.RecyclerItemPhotoBinding
import com.hoc081098.paginationmviflow.databinding.RecyclerItemPlaceholderBinding
import com.hoc081098.paginationmviflow.ui.main.MainContract.Item
import com.hoc081098.paginationmviflow.ui.main.MainContract.PlaceholderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

private object DiffUtilItemCallback : DiffUtil.ItemCallback<Item>() {
  override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean {
    return when {
      oldItem is Item.Placeholder && newItem is Item.Placeholder -> true
      oldItem is Item.HorizontalList && newItem is Item.HorizontalList -> true
      oldItem is Item.Photo && newItem is Item.Photo -> oldItem.photo.id == newItem.photo.id
      else -> oldItem == newItem
    }
  }

  override fun areContentsTheSame(oldItem: Item, newItem: Item) = oldItem == newItem

  override fun getChangePayload(oldItem: Item, newItem: Item): Any? {
    return when {
      oldItem is Item.Placeholder && newItem is Item.Placeholder -> newItem.state
      oldItem is Item.HorizontalList && newItem is Item.HorizontalList -> newItem
      oldItem is Item.Photo && newItem is Item.Photo -> newItem.photo
      else -> null
    }
  }
}

class MainAdapter(
  private val coroutineScope: CoroutineScope,
  private val viewPool: RecyclerView.RecycledViewPool
) :
  ListAdapter<Item, MainAdapter.VH>(DiffUtilItemCallback) {
  private val scrollToFirstSF = MutableSharedFlow<Unit>()
  private var layoutManagerSavedState: Parcelable? = null

  private val retrySF = MutableSharedFlow<Unit>()
  val retryFlow get() = retrySF.asFlow()

  private val loadNextPageHorizontalSF = MutableSharedFlow<Unit>()
  val loadNextPageHorizontalFlow get() = loadNextPageHorizontalSF.asFlow()

  private val retryNextPageHorizontalSF = MutableSharedFlow<Unit>()
  val retryNextPageHorizontalFlow get() = retryNextPageHorizontalSF.asFlow()

  private val retryHorizontalSF = MutableSharedFlow<Unit>()
  val retryHorizontalFlow get() = retryHorizontalSF.asFlow()

  /**
   *
   */

  override fun onCreateViewHolder(parent: ViewGroup, @LayoutRes viewType: Int): VH {
    val itemView = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
    return when (viewType) {
      R.layout.recycler_item_photo -> PhotoVH(RecyclerItemPhotoBinding.bind(itemView))
      R.layout.recycler_item_placeholder -> PlaceHolderVH(
        RecyclerItemPlaceholderBinding.bind(itemView),
        parent
      )
      R.layout.recycler_item_horizontal_list -> HorizontalListVH(
        RecyclerItemHorizontalListBinding.bind(itemView),
        parent
      )
      else -> error("Unknown viewType=$viewType")
    }
  }

  override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

  override fun onBindViewHolder(holder: VH, position: Int, payloads: List<Any>) {
    if (payloads.isEmpty()) return holder.bind(getItem(position))
    Log.d("###", "[PAYLOAD] MAIN size=${payloads.size}")
    payloads.forEach { payload ->
      Log.d("###", "[PAYLOAD] $payload")
      when {
        payload is PlaceholderState && holder is PlaceHolderVH -> holder.update(payload)
        payload is Item.HorizontalList && holder is HorizontalListVH -> holder.update(
          payload
        )
        payload is Item.Photo && holder is PhotoVH -> holder.update(payload)
      }
    }
  }

  @LayoutRes
  override fun getItemViewType(position: Int) = getItem(position).viewType

  /**
   *
   */

  abstract class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
    abstract fun bind(item: Item)
  }

  private class PhotoVH(private val binding: RecyclerItemPhotoBinding) : VH(binding.root) {
    override fun bind(item: Item) {
      if (item !is Item.Photo) return
      update(item)
    }

    fun update(item: Item.Photo) = binding.run {
      image.load(item.photo.thumbnailUrl) {
        crossfade(true)
        placeholder(R.drawable.placeholder)
        error(R.drawable.placeholder)
      }
    }
  }

  private inner class PlaceHolderVH(
    private val binding: RecyclerItemPlaceholderBinding,
    parent: ViewGroup
  ) : VH(binding.root) {
    init {
      binding
        .buttonRetry
        .clicks()
        .takeUntil(parent.detaches())
        .filter {
          val position = bindingAdapterPosition
          if (position == RecyclerView.NO_POSITION) {
            false
          } else {
            (getItem(position) as? Item.Placeholder)?.state is PlaceholderState.Error
          }
        }
        .onEach { retrySF.emit(Unit) }
        .launchIn(coroutineScope)
    }

    override fun bind(item: Item) {
      if (item !is Item.Placeholder) return
      update(item.state)
    }

    fun update(state: PlaceholderState) = binding.run {
      Log.d("###", "[BIND] $state")

      when (state) {
        PlaceholderState.Loading -> {
          progressBar.isInvisible = false
          textError.isInvisible = true
          buttonRetry.isInvisible = true
        }
        PlaceholderState.Idle -> {
          progressBar.isInvisible = true
          textError.isInvisible = true
          buttonRetry.isInvisible = true
        }
        is PlaceholderState.Error -> {
          progressBar.isInvisible = true
          textError.isInvisible = false
          buttonRetry.isInvisible = false
          textError.text = state.error.message
        }
      }
    }
  }

  private inner class HorizontalListVH(
    private val binding: RecyclerItemHorizontalListBinding,
    parent: ViewGroup
  ) : VH(binding.root) {
    private val adapter = HorizontalAdapter(coroutineScope)
    private val visibleThreshold get() = 2
    val linearLayoutManager = LinearLayoutManager(
      itemView.context,
      RecyclerView.HORIZONTAL,
      false
    )

    init {
      val recyclerView = binding.recyclerHorizontal.apply {
        setRecycledViewPool(viewPool)

        setHasFixedSize(true)
        adapter = this@HorizontalListVH.adapter
        layoutManager = this@HorizontalListVH.linearLayoutManager

        addItemDecoration(object : RecyclerView.ItemDecoration() {
          override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
          ) {
            outRect.run {
              right =
                if (parent.getChildAdapterPosition(view) == parent.adapter!!.itemCount - 1) 0 else 8
              left = 0
              top = 0
              bottom = 0
            }
          }
        })
      }

      adapter
        .retryNextPageFlow
        .onEach { retryNextPageHorizontalSF.emit(Unit) }
        .launchIn(coroutineScope)

      binding.buttonRetryHorizontal
        .clicks()
        .takeUntil(parent.detaches())
        .filter {
          val position = bindingAdapterPosition
          if (position == RecyclerView.NO_POSITION) {
            false
          } else {
            (getItem(position) as? Item.HorizontalList)?.shouldRetry() == true
          }
        }
        .onEach { retryHorizontalSF.emit(Unit) }
        .launchIn(coroutineScope)

      recyclerView
        .scrollEvents()
        .takeUntil(parent.detaches())
        .filter { (_, dx, _) ->
          val layoutManager = recyclerView.layoutManager as LinearLayoutManager
          dx > 0 && layoutManager.findLastVisibleItemPosition() + visibleThreshold >= layoutManager.itemCount
        }
        .onEach { loadNextPageHorizontalSF.emit(Unit) }
        .launchIn(coroutineScope)

      scrollToFirstSF
        .onEach { recyclerView.scrollToPosition(0) }
        .launchIn(coroutineScope)
    }

    override fun bind(item: Item) {
      if (item !is Item.HorizontalList) return
      update(item)
    }

    fun update(item: Item.HorizontalList) = binding.run {
      progressBarHorizontal.isInvisible = !item.isLoading

      textErrorHorizontal.isInvisible = item.error == null
      buttonRetryHorizontal.isInvisible = item.error == null
      textErrorHorizontal.text = item.error?.message

      adapter.submitList(item.items)

      layoutManagerSavedState?.let {
        linearLayoutManager.onRestoreInstanceState(it)
        layoutManagerSavedState = null
      }
    }
  }

  /**
   *
   */

  override fun onViewRecycled(holder: VH) {
    super.onViewRecycled(holder)
    if (holder is HorizontalListVH) {
      layoutManagerSavedState = holder.linearLayoutManager.onSaveInstanceState()
    }
  }

  suspend fun scrollHorizontalListToFirst() = scrollToFirstSF.emit(Unit)
}