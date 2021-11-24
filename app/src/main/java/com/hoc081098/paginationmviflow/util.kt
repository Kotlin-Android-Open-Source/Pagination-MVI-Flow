package com.hoc081098.paginationmviflow

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.CheckResult
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

typealias FlowTransformer<I, O> = (Flow<I>) -> Flow<O>

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
