package com.hoc081098.paginationmviflow

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.CheckResult
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

fun interface FlowTransformer<I, O> {
  fun transform(input: Flow<I>): Flow<O>
}

@Suppress("NOTHING_TO_INLINE")
inline fun <I, O> Flow<I>.pipe(transformer: FlowTransformer<I, O>) =
  transformer.transform(this)

val Context.isOrientationPortrait get() = this.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

fun Context.toast(text: CharSequence) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

fun Fragment.toast(text: CharSequence) = requireContext().toast(text)

@OptIn(ExperimentalCoroutinesApi::class)
@CheckResult
fun SwipeRefreshLayout.refreshes(): Flow<Unit> {
  return callbackFlow {
    setOnRefreshListener { trySend(Unit) }
    awaitClose { setOnRefreshListener(null) }
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
@CheckResult
fun View.clicks(): Flow<View> {
  return callbackFlow {
    setOnClickListener { trySend(it) }
    awaitClose { setOnClickListener(null) }
  }
}

data class RecyclerViewScrollEvent(val view: RecyclerView, val dx: Int, val dy: Int)

@OptIn(ExperimentalCoroutinesApi::class)
fun RecyclerView.scrollEvents(): Flow<RecyclerViewScrollEvent> {
  return callbackFlow {
    val listener = object : RecyclerView.OnScrollListener() {
      override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        trySend(
          RecyclerViewScrollEvent(
            view = recyclerView,
            dx = dx,
            dy = dy
          )
        )
      }
    }
    addOnScrollListener(listener)
    awaitClose { removeOnScrollListener(listener) }
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun ViewGroup.detaches(): Flow<Unit> {
  return callbackFlow {
    val listener = object : View.OnAttachStateChangeListener {
      override fun onViewAttachedToWindow(v: View) = Unit
      override fun onViewDetachedFromWindow(v: View) {
        trySend(Unit)
      }
    }
    addOnAttachStateChangeListener(listener)
    awaitClose { removeOnAttachStateChangeListener(listener) }
  }
}

@Deprecated(
  message = "Should use Flow<T>.collectInViewLifecycle",
  replaceWith = ReplaceWith(
    "this.collectInViewLifecycle(owner, minActiveState, action)"
  ),
  level = DeprecationLevel.WARNING,
)
inline fun <T> Flow<T>.collectIn(
  owner: Fragment,
  minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
  crossinline action: suspend (value: T) -> Unit,
) = collectIn(this as LifecycleOwner, minActiveState, action)

inline fun <T> Flow<T>.collectIn(
  owner: LifecycleOwner,
  minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
  crossinline action: suspend (value: T) -> Unit,
): Job = owner.lifecycleScope.launch {
  owner.repeatOnLifecycle(state = minActiveState) { collect { action(it) } }
}

/**
 * Launches a new coroutine and repeats `block` every time the Fragment's viewLifecycleOwner
 * is in and out of `minActiveState` lifecycle state.
 */
@Suppress("unused")
inline fun <T> Flow<T>.collectInViewLifecycle(
  fragment: Fragment,
  minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
  crossinline action: suspend (value: T) -> Unit,
): Job = collectIn(
  owner = fragment.viewLifecycleOwner,
  minActiveState = minActiveState,
  action = action,
)

/*
fun main() = runBlocking {
  println("Start...")

  val mutableStateFlow = MutableStateFlow(1)

  launch {
    while (true) {
      delay(333)
      mutableStateFlow.value = Random.nextInt()
      println("State emit ${mutableStateFlow.value}")
    }
  }

  generateSequence(0) { it + 1 }.asFlow()
    .onEach { delay(1000) }
    .drop(1)
    .onEach { println("Emit $it") }
    .withLatestFrom(mutableStateFlow)
    .onEach { println("With latest $it") }
    .collect { }
}*/

/*fun main() = runBlocking {
  val other = flow<Nothing> {
    delay(3333)
    error("???")
  }

  val other2 = flow<Int> {
    delay(3333)
    emit(22)
  }
  val never = flow<Nothing> { delay(Long.MAX_VALUE) }.onCompletion { println("1 completed") }

  generateSequence(0) { it + 1 }.asFlow()
    .onEach { delay(200) }
    .take(20)
    .onEach { println("Emit $it") }
    .takeUntil(never)
    .catch { println("Error $it") }
    .onCompletion { println("Done... $it") }
    .collect { }
}*/
