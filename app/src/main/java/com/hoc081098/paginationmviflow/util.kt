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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

typealias FlowTransformer<I, O> = (Flow<I>) -> Flow<O>

val Context.isOrientationPortrait get() = this.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

fun Context.toast(text: CharSequence) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

fun Fragment.toast(text: CharSequence) = requireContext().toast(text)

@Suppress("NOTHING_TO_INLINE")
inline fun <T> SharedFlow<T>.asFlow(): Flow<T> = this

@OptIn(ExperimentalCoroutinesApi::class)
@CheckResult
fun SwipeRefreshLayout.refreshes(): Flow<Unit> {
  return callbackFlow {
    setOnRefreshListener { tryOffer(Unit) }
    awaitClose { setOnRefreshListener(null) }
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
@CheckResult
fun View.clicks(): Flow<View> {
  return callbackFlow {
    setOnClickListener { tryOffer(it) }
    awaitClose { setOnClickListener(null) }
  }
}

data class RecyclerViewScrollEvent(val view: RecyclerView, val dx: Int, val dy: Int)

@OptIn(ExperimentalCoroutinesApi::class)
fun RecyclerView.scrollEvents(): Flow<RecyclerViewScrollEvent> {
  return callbackFlow {
    val listener = object : RecyclerView.OnScrollListener() {
      override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        tryOffer(
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
        tryOffer(Unit)
      }
    }
    addOnAttachStateChangeListener(listener)
    awaitClose { removeOnAttachStateChangeListener(listener) }
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun <T, R> Flow<T>.takeUntil(other: Flow<R>): Flow<T> {
  return channelFlow {
    launch {
      other.take(1).collect { close() }
    }

    launch {
      collect { send(it) }
    }
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> SendChannel<T>.tryOffer(t: T): Boolean = if (isClosedForSend) false else offer(t)

fun <T, R> Flow<T>.flatMapFirst(transform: suspend (value: T) -> Flow<R>): Flow<R> =
  map(transform).flattenFirst()

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalStdlibApi::class)
fun <T> Flow<Flow<T>>.flattenFirst(): Flow<T> = channelFlow {
  val outerScope = this
  val busy = AtomicBoolean(false)
  collect { inner ->
    if (busy.compareAndSet(false, true)) {
      launch {
        try {
          inner.collect { outerScope.send(it) }
          busy.set(false)
        } catch (e: CancellationException) {
          // cancel outer scope on cancellation exception, too
          outerScope.cancel(e)
        }
      }
    }
  }
}

fun <A, B> Flow<A>.withLatestFrom(other: Flow<B>): Flow<Pair<A, B>> =
  withLatestFrom(other) { a, b -> a to b }

private object UNINITIALIZED

@OptIn(ExperimentalStdlibApi::class)
fun <A, B, R> Flow<A>.withLatestFrom(other: Flow<B>, transform: suspend (A, B) -> R): Flow<R> {
  return flow {
    coroutineScope {
      val latestB = AtomicReference<Any>(UNINITIALIZED)
      val outerScope = this

      launch {
        try {
          other.collect { latestB.set(it) }
        } catch (e: CancellationException) {
          outerScope.cancel(e) // cancel outer scope on cancellation exception, too
        }
      }

      collect { a ->
        val b = latestB.get()
        if (b != UNINITIALIZED) {
          @Suppress("UNCHECKED_CAST")
          emit(transform(a, b as B))
        }
      }
    }
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
