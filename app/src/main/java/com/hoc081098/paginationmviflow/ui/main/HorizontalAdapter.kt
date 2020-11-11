package com.hoc081098.paginationmviflow.ui.main

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hoc081098.paginationmviflow.R
import com.hoc081098.paginationmviflow.asFlow
import com.hoc081098.paginationmviflow.clicks
import com.hoc081098.paginationmviflow.databinding.RecyclerItemHorizontalPlaceholderBinding
import com.hoc081098.paginationmviflow.databinding.RecyclerItemHorizontalPostBinding
import com.hoc081098.paginationmviflow.ui.main.MainContract.Item.HorizontalList.HorizontalItem
import com.hoc081098.paginationmviflow.ui.main.MainContract.PlaceholderState
import com.hoc081098.paginationmviflow.ui.main.MainContract.PostVS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

private object HorizontalItemItemCallback : DiffUtil.ItemCallback<HorizontalItem>() {
  override fun areItemsTheSame(oldItem: HorizontalItem, newItem: HorizontalItem): Boolean {
    return when {
      oldItem is HorizontalItem.Post && newItem is HorizontalItem.Post -> oldItem.post.id == newItem.post.id
      oldItem is HorizontalItem.Placeholder && newItem is HorizontalItem.Placeholder -> true
      else -> oldItem == newItem
    }
  }

  override fun areContentsTheSame(oldItem: HorizontalItem, newItem: HorizontalItem) =
      oldItem == newItem

  override fun getChangePayload(oldItem: HorizontalItem, newItem: HorizontalItem): Any? {
    return when {
      oldItem is HorizontalItem.Post && newItem is HorizontalItem.Post -> newItem.post
      oldItem is HorizontalItem.Placeholder && newItem is HorizontalItem.Placeholder -> newItem.state
      else -> null
    }
  }
}

class HorizontalAdapter(
    private val coroutineScope: CoroutineScope,
) : ListAdapter<HorizontalItem, HorizontalAdapter.VH>(HorizontalItemItemCallback) {

  private val retryNextPageSF = MutableSharedFlow<Unit>()
  val retryNextPageFlow get() = retryNextPageSF.asFlow()

  override fun onCreateViewHolder(parent: ViewGroup, @LayoutRes viewType: Int): VH {
    val itemView = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
    return when (viewType) {
      R.layout.recycler_item_horizontal_post -> PostVH(RecyclerItemHorizontalPostBinding.bind(itemView))
      R.layout.recycler_item_horizontal_placeholder -> PlaceholderVH(RecyclerItemHorizontalPlaceholderBinding.bind(itemView))
      else -> error("Unknown viewType=$viewType")
    }
  }

  override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

  override fun onBindViewHolder(holder: VH, position: Int, payloads: List<Any>) {
    if (payloads.isEmpty()) return holder.bind(getItem(position))
    Log.d("###", "[PAYLOAD] HORIZONTAL size=${payloads.size}")
    payloads.forEach { payload ->
      when {
        payload is PostVS && holder is PostVH -> holder.update(payload)
        payload is PlaceholderState && holder is PlaceholderVH -> holder.update(payload)
      }
    }
  }

  @LayoutRes
  override fun getItemViewType(position: Int) = getItem(position).viewType

  abstract class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
    abstract fun bind(item: HorizontalItem)
  }

  private class PostVH(private val binding: RecyclerItemHorizontalPostBinding) : VH(binding.root) {
    override fun bind(item: HorizontalItem) {
      if (item !is HorizontalItem.Post) return
      update(item.post)
    }

    fun update(post: PostVS) = binding.run {
      textTitle.text = post.title
      textBody.text = post.body
    }
  }

  private inner class PlaceholderVH(private val binding: RecyclerItemHorizontalPlaceholderBinding) : VH(binding.root) {
    init {
      binding
          .buttonRetry
          .clicks()
          .filter {
            val position = bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) {
              false
            } else {
              (getItem(position) as? HorizontalItem.Placeholder)?.state is PlaceholderState.Error
            }
          }
          .onEach { retryNextPageSF.emit(Unit) }
          .launchIn(coroutineScope)
    }

    override fun bind(item: HorizontalItem) {
      if (item !is HorizontalItem.Placeholder) return
      update(item.state)
    }

    fun update(state: PlaceholderState) = binding.run {
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
}