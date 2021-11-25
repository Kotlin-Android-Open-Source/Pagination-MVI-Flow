package com.hoc081098.paginationmviflow.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoc081098.flowext.flatMapFirst
import com.hoc081098.flowext.withLatestFrom
import com.hoc081098.paginationmviflow.FlowTransformer
import com.hoc081098.paginationmviflow.ui.main.MainContract.Interactor
import com.hoc081098.paginationmviflow.ui.main.MainContract.PartialStateChange
import com.hoc081098.paginationmviflow.ui.main.MainContract.PartialStateChange.PhotoFirstPage
import com.hoc081098.paginationmviflow.ui.main.MainContract.PartialStateChange.PhotoNextPage
import com.hoc081098.paginationmviflow.ui.main.MainContract.PartialStateChange.PostFirstPage
import com.hoc081098.paginationmviflow.ui.main.MainContract.PartialStateChange.PostNextPage
import com.hoc081098.paginationmviflow.ui.main.MainContract.PartialStateChange.Refresh
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.take
import javax.inject.Inject
import com.hoc081098.paginationmviflow.ui.main.MainContract.SingleEvent as SE
import com.hoc081098.paginationmviflow.ui.main.MainContract.ViewIntent as VI
import com.hoc081098.paginationmviflow.ui.main.MainContract.ViewState as VS

@OptIn(
  ExperimentalCoroutinesApi::class,
  FlowPreview::class
)
@HiltViewModel
class MainVM @Inject constructor(private val interactor: Interactor) : ViewModel() {
  private val initialVS = VS.initial()

  private val _stateSF = MutableStateFlow(initialVS)
  private val _singleEventChannel = Channel<SE>(Channel.UNLIMITED)
  private val _intentSF = MutableSharedFlow<VI>(extraBufferCapacity = 64)

  val stateFlow: StateFlow<VS> get() = _stateSF.asStateFlow()
  val singleEventFlow: Flow<SE> get() = _singleEventChannel.receiveAsFlow()

  suspend fun processIntent(intent: VI) = _intentSF.emit(intent)

  private val initialProcessor:
    FlowTransformer<VI.Initial, PartialStateChange> = { intents ->
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

  private val nextPageProcessor: FlowTransformer<VI.LoadNextPage, PartialStateChange> =
    { intents ->
      intents
        .withLatestFrom(_stateSF)
        .filter { (_, vs) -> vs.canLoadNextPage() }
        .map { (_, vs) -> vs.photoItems.size }
        .flatMapFirst { interactor.photoNextPageChanges(start = it, limit = PHOTO_PAGE_SIZE) }
    }

  private val retryLoadPageProcessor: FlowTransformer<VI.RetryLoadPage, PartialStateChange> =
    { intents ->
      intents
        .withLatestFrom(_stateSF)
        .filter { (_, vs) -> vs.shouldRetry() }
        .map { (_, vs) -> vs.photoItems.size }
        .flatMapFirst { interactor.photoNextPageChanges(start = it, limit = PHOTO_PAGE_SIZE) }
    }

  private val loadNextPageHorizontalProcessor: FlowTransformer<VI.LoadNextPageHorizontal, PartialStateChange> =
    { intents ->
      intents
        .withLatestFrom(_stateSF)
        .filter { (_, vs) -> vs.canLoadNextPageHorizontal() }
        .map { (_, vs) -> vs.getHorizontalListCount() }
        .flatMapFirst { interactor.postNextPageChanges(start = it, limit = POST_PAGE_SIZE) }
    }

  private val retryLoadPageHorizontalProcessor: FlowTransformer<VI.RetryLoadPageHorizontal, PartialStateChange> =
    { intents ->
      intents
        .withLatestFrom(_stateSF)
        .filter { (_, vs) -> vs.shouldRetryNextPageHorizontal() }
        .map { (_, vs) -> vs.getHorizontalListCount() }
        .flatMapFirst { interactor.postNextPageChanges(start = it, limit = POST_PAGE_SIZE) }
    }

  private val retryHorizontalProcessor: FlowTransformer<VI.RetryHorizontal, PartialStateChange> =
    { intents ->
      intents
        .withLatestFrom(_stateSF)
        .filter { (_, vs) -> vs.shouldRetryHorizontal() }
        .flatMapFirst { interactor.postFirstPageChanges(limit = POST_PAGE_SIZE) }
    }

  private val refreshProcessor: FlowTransformer<VI.Refresh, PartialStateChange> =
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

  private val toPartialStateChange: FlowTransformer<VI, PartialStateChange> = { intents ->
    intents
      .shareIn(viewModelScope, SharingStarted.WhileSubscribed())
      .let { shared ->
        merge(
          shared.filterIsInstance<VI.Initial>()
            .let(initialProcessor),
          shared.filterIsInstance<VI.LoadNextPage>()
            .let(nextPageProcessor),
          shared.filterIsInstance<VI.RetryLoadPage>()
            .let(retryLoadPageProcessor),
          shared.filterIsInstance<VI.LoadNextPageHorizontal>()
            .let(loadNextPageHorizontalProcessor),
          shared.filterIsInstance<VI.RetryLoadPageHorizontal>()
            .let(retryLoadPageHorizontalProcessor),
          shared.filterIsInstance<VI.RetryHorizontal>()
            .let(retryHorizontalProcessor),
          shared.filterIsInstance<VI.Refresh>()
            .let(refreshProcessor)
        )
      }
      .let(sendSingleEvent)
  }

  private val sendSingleEvent: FlowTransformer<PartialStateChange, PartialStateChange> =
    { changes ->
      changes
        .onEach { change ->
          when (change) {
            is PhotoFirstPage.Data -> if (change.photos.isEmpty()) _singleEventChannel.send(SE.HasReachedMax)
            is PhotoFirstPage.Error -> _singleEventChannel.send(SE.GetPhotosFailure(change.error))
            PhotoFirstPage.Loading -> Unit
            // /
            is PhotoNextPage.Data -> if (change.photos.isEmpty()) _singleEventChannel.send(SE.HasReachedMax)
            is PhotoNextPage.Error -> _singleEventChannel.send(SE.GetPhotosFailure(change.error))
            PhotoNextPage.Loading -> Unit
            // /
            is PostFirstPage.Data -> if (change.posts.isEmpty()) _singleEventChannel.send(SE.HasReachedMaxHorizontal)
            is PostFirstPage.Error -> _singleEventChannel.send(SE.GetPostsFailure(change.error))
            PostFirstPage.Loading -> Unit
            // /
            is PostNextPage.Data -> if (change.posts.isEmpty()) _singleEventChannel.send(SE.HasReachedMaxHorizontal)
            is PostNextPage.Error -> _singleEventChannel.send(SE.GetPostsFailure(change.error))
            PostNextPage.Loading -> Unit
            // /
            is Refresh.Success -> _singleEventChannel.send(SE.RefreshSuccess)
            is Refresh.Error -> _singleEventChannel.send(SE.RefreshFailure(change.error))
            Refresh.Refreshing -> Unit
          }
        }
    }

  init {
    _intentSF
      .let(intentFilterer)
      .let(toPartialStateChange)
      .scan(initialVS) { vs, change -> change.reduce(vs) }
      .onEach { _stateSF.value = it }
      .launchIn(viewModelScope)
  }

  private companion object {
    val intentFilterer: FlowTransformer<VI, VI> = { intents ->
      merge(
        intents.filterIsInstance<VI.Initial>().take(1),
        intents.filter { it !is VI.Initial }
      )
    }

    const val PHOTO_PAGE_SIZE = 20
    const val POST_PAGE_SIZE = 10
  }
}
