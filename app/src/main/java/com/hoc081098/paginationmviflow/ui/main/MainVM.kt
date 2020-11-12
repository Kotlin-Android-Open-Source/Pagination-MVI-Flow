package com.hoc081098.paginationmviflow.ui.main

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoc081098.paginationmviflow.FlowTransformer
import com.hoc081098.paginationmviflow.asFlow
import com.hoc081098.paginationmviflow.flatMapFirst
import com.hoc081098.paginationmviflow.ui.main.MainContract.*
import com.hoc081098.paginationmviflow.ui.main.MainContract.PartialStateChange.*
import com.hoc081098.paginationmviflow.withLatestFrom
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainVM @ViewModelInject constructor(private val interactor: Interactor) : ViewModel() {
  private val initialVS = ViewState.initial()

  private val _stateSF = MutableStateFlow(initialVS)
  private val _singleEventSF = MutableSharedFlow<SingleEvent>(extraBufferCapacity = 64)
  private val _intentSF = MutableSharedFlow<ViewIntent>(extraBufferCapacity = 64)

  val stateFlow get() = _stateSF.asStateFlow()
  val singleEventFlow get() = _singleEventSF.asFlow()

  suspend fun processIntent(intent: ViewIntent) = _intentSF.emit(intent)

  private val initialProcessor:
      FlowTransformer<ViewIntent.Initial, PartialStateChange> = { intents ->
    intents
      .withLatestFrom(_stateSF)
      .filter { (_, vs) -> vs.photoItems.isEmpty() }
      .flatMapMerge {
        merge(
          interactor.photoFirstPageChanges(limit = PHOTO_PAGE_SIZE),
          interactor.postFirstPageChanges(limit = POST_PAGE_SIZE)
        )
      }
  }

  private val nextPageProcessor: FlowTransformer<ViewIntent.LoadNextPage, PartialStateChange> =
    { intents ->
      intents
        .withLatestFrom(_stateSF)
        .filter { (_, vs) -> vs.canLoadNextPage() }
        .map { (_, vs) -> vs.photoItems.size }
        .flatMapFirst { interactor.photoNextPageChanges(start = it, limit = PHOTO_PAGE_SIZE) }
    }

  private val retryLoadPageProcessor: FlowTransformer<ViewIntent.RetryLoadPage, PartialStateChange> =
    { intents ->
      intents
        .withLatestFrom(_stateSF)
        .filter { (_, vs) -> vs.shouldRetry() }
        .map { (_, vs) -> vs.photoItems.size }
        .flatMapFirst { interactor.photoNextPageChanges(start = it, limit = PHOTO_PAGE_SIZE) }
    }

  private val loadNextPageHorizontalProcessor: FlowTransformer<ViewIntent.LoadNextPageHorizontal, PartialStateChange> =
    { intents ->
      intents
        .withLatestFrom(_stateSF)
        .filter { (_, vs) -> vs.canLoadNextPageHorizontal() }
        .map { (_, vs) -> vs.getHorizontalListCount() }
        .flatMapFirst { interactor.postNextPageChanges(start = it, limit = POST_PAGE_SIZE) }
    }

  private val retryLoadPageHorizontalProcessor: FlowTransformer<ViewIntent.RetryLoadPageHorizontal, PartialStateChange> =
    { intents ->
      intents
        .withLatestFrom(_stateSF)
        .filter { (_, vs) -> vs.shouldRetryNextPageHorizontal() }
        .map { (_, vs) -> vs.getHorizontalListCount() }
        .flatMapFirst { interactor.postNextPageChanges(start = it, limit = POST_PAGE_SIZE) }
    }

  private val retryHorizontalProcessor: FlowTransformer<ViewIntent.RetryHorizontal, PartialStateChange> =
    { intents ->
      intents
        .withLatestFrom(_stateSF)
        .filter { (_, vs) -> vs.shouldRetryHorizontal() }
        .flatMapFirst { interactor.postFirstPageChanges(limit = POST_PAGE_SIZE) }
    }

  private val refreshProcessor: FlowTransformer<ViewIntent.Refresh, PartialStateChange> =
    { intents ->
      intents
        .withLatestFrom(_stateSF)
        .filter { (_, vs) -> vs.enableRefresh }
        .flatMapFirst {
          interactor.refreshAll(
            limitPhoto = PHOTO_PAGE_SIZE,
            limitPost = POST_PAGE_SIZE
          )
        }
    }

  private val toPartialStateChange: FlowTransformer<ViewIntent, PartialStateChange> = { intents ->
    intents
      .shareIn(viewModelScope, SharingStarted.WhileSubscribed())
      .let { shared ->
        merge(
          shared.filterIsInstance<ViewIntent.Initial>().let(initialProcessor),
          shared.filterIsInstance<ViewIntent.LoadNextPage>().let(nextPageProcessor),
          shared.filterIsInstance<ViewIntent.RetryLoadPage>().let(retryLoadPageProcessor),
          shared.filterIsInstance<ViewIntent.LoadNextPageHorizontal>().let(
            loadNextPageHorizontalProcessor
          ),
          shared.filterIsInstance<ViewIntent.RetryLoadPageHorizontal>().let(
            retryLoadPageHorizontalProcessor
          ),
          shared.filterIsInstance<ViewIntent.RetryHorizontal>().let(retryHorizontalProcessor),
          shared.filterIsInstance<ViewIntent.Refresh>().let(refreshProcessor)
        )
      }
      .let(sendSingleEvent)
  }

  private val sendSingleEvent: FlowTransformer<PartialStateChange, PartialStateChange> =
    { changes ->
      changes
        .onEach { change ->
          when (change) {
            is PhotoFirstPage.Data -> if (change.photos.isEmpty()) _singleEventSF.emit(SingleEvent.HasReachedMax)
            is PhotoFirstPage.Error -> _singleEventSF.emit(SingleEvent.GetPhotosFailure(change.error))
            PhotoFirstPage.Loading -> Unit
            ///
            is PhotoNextPage.Data -> if (change.photos.isEmpty()) _singleEventSF.emit(SingleEvent.HasReachedMax)
            is PhotoNextPage.Error -> _singleEventSF.emit(SingleEvent.GetPhotosFailure(change.error))
            PhotoNextPage.Loading -> Unit
            ///
            is PostFirstPage.Data -> if (change.posts.isEmpty()) _singleEventSF.emit(SingleEvent.HasReachedMaxHorizontal)
            is PostFirstPage.Error -> _singleEventSF.emit(SingleEvent.GetPostsFailure(change.error))
            PostFirstPage.Loading -> Unit
            ///
            is PostNextPage.Data -> if (change.posts.isEmpty()) _singleEventSF.emit(SingleEvent.HasReachedMaxHorizontal)
            is PostNextPage.Error -> _singleEventSF.emit(SingleEvent.GetPostsFailure(change.error))
            PostNextPage.Loading -> Unit
            ///
            is Refresh.Success -> _singleEventSF.emit(SingleEvent.RefreshSuccess)
            is Refresh.Error -> _singleEventSF.emit(SingleEvent.RefreshFailure(change.error))
            Refresh.Refreshing -> Unit
          }
        }
    }

  private val intentFilter: FlowTransformer<ViewIntent, ViewIntent> = { intents ->
    merge(
      intents.filterIsInstance<ViewIntent.Initial>().take(1),
      intents.filter { it !is ViewIntent.Initial }
    )
  }

  init {
    _intentSF
      .let(intentFilter)
      .let(toPartialStateChange)
      .scan(initialVS) { vs, change -> change.reduce(vs) }
      .onEach { _stateSF.value = it }
      .launchIn(viewModelScope)
  }

  private companion object {
    const val PHOTO_PAGE_SIZE = 20
    const val POST_PAGE_SIZE = 10
  }
}

