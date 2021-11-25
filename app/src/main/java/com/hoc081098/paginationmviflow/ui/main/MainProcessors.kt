package com.hoc081098.paginationmviflow.ui.main

import com.hoc081098.flowext.flatMapFirst
import com.hoc081098.flowext.withLatestFrom
import com.hoc081098.paginationmviflow.FlowTransformer
import com.hoc081098.paginationmviflow.ui.main.MainContract.PartialStateChange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import javax.inject.Inject
import com.hoc081098.paginationmviflow.ui.main.MainContract.ViewIntent as VI
import com.hoc081098.paginationmviflow.ui.main.MainContract.ViewState as VS

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainProcessors @Inject constructor(
  private val interactor: MainContract.Interactor,
) {
  internal fun getInitialProcessor(stateFlow: StateFlow<VS>): FlowTransformer<VI.Initial, PartialStateChange> =
    FlowTransformer { intents ->
      intents
        .withLatestFrom(stateFlow)
        .filter { (_, vs) -> vs.photoItems.isEmpty() }
        .flatMapMerge {
          merge(
            interactor.photoFirstPageChanges(limit = MainVM.PHOTO_PAGE_SIZE),
            interactor.postFirstPageChanges(limit = MainVM.POST_PAGE_SIZE)
          )
        }
    }

  internal fun getNextPageProcessor(stateFlow: StateFlow<VS>): FlowTransformer<VI.LoadNextPage, PartialStateChange> =
    FlowTransformer { intents ->
      intents
        .withLatestFrom(stateFlow)
        .filter { (_, vs) -> vs.canLoadNextPage() }
        .map { (_, vs) -> vs.photoItems.size }
        .flatMapFirst {
          interactor.photoNextPageChanges(
            start = it,
            limit = MainVM.PHOTO_PAGE_SIZE
          )
        }
    }

  internal fun getRetryLoadPageProcessor(stateFlow: StateFlow<VS>): FlowTransformer<VI.RetryLoadPage, PartialStateChange> =
    FlowTransformer { intents ->
      intents
        .withLatestFrom(stateFlow)
        .filter { (_, vs) -> vs.shouldRetry() }
        .map { (_, vs) -> vs.photoItems.size }
        .flatMapFirst {
          interactor.photoNextPageChanges(
            start = it,
            limit = MainVM.PHOTO_PAGE_SIZE
          )
        }
    }

  internal fun getLoadNextPageHorizontalProcessor(stateFlow: StateFlow<VS>): FlowTransformer<VI.LoadNextPageHorizontal, PartialStateChange> =
    FlowTransformer { intents ->
      intents
        .withLatestFrom(stateFlow)
        .filter { (_, vs) -> vs.canLoadNextPageHorizontal() }
        .map { (_, vs) -> vs.getHorizontalListCount() }
        .flatMapFirst { interactor.postNextPageChanges(start = it, limit = MainVM.POST_PAGE_SIZE) }
    }

  internal fun getRetryLoadPageHorizontalProcessor(stateFlow: StateFlow<VS>): FlowTransformer<VI.RetryLoadPageHorizontal, PartialStateChange> =
    FlowTransformer { intents ->
      intents
        .withLatestFrom(stateFlow)
        .filter { (_, vs) -> vs.shouldRetryNextPageHorizontal() }
        .map { (_, vs) -> vs.getHorizontalListCount() }
        .flatMapFirst { interactor.postNextPageChanges(start = it, limit = MainVM.POST_PAGE_SIZE) }
    }

  internal fun getRetryHorizontalProcessor(stateFlow: StateFlow<VS>): FlowTransformer<VI.RetryHorizontal, PartialStateChange> =
    FlowTransformer { intents ->
      intents
        .withLatestFrom(stateFlow)
        .filter { (_, vs) -> vs.shouldRetryHorizontal() }
        .flatMapFirst { interactor.postFirstPageChanges(limit = MainVM.POST_PAGE_SIZE) }
    }

  internal fun getRefreshProcessor(stateFlow: StateFlow<VS>): FlowTransformer<VI.Refresh, PartialStateChange> =
    FlowTransformer { intents ->
      intents
        .withLatestFrom(stateFlow)
        .filter { (_, vs) -> vs.enableRefresh }
        .flatMapFirst {
          interactor.refreshAll(
            limitPhoto = MainVM.PHOTO_PAGE_SIZE,
            limitPost = MainVM.POST_PAGE_SIZE
          )
        }
    }
}
