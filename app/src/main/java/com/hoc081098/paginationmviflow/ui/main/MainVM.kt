package com.hoc081098.paginationmviflow.ui.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.distinctUntilChanged
import com.hoc081098.paginationmviflow.asObservable
import com.hoc081098.paginationmviflow.domain.dispatchers_schedulers.RxSchedulerProvider
import com.hoc081098.paginationmviflow.exhaustMap
import com.hoc081098.paginationmviflow.ui.main.MainContract.*
import com.hoc081098.paginationmviflow.ui.main.MainContract.PartialStateChange.*
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.ofType
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

@ExperimentalCoroutinesApi
class MainVM @Inject constructor(
  private val interactor: Interactor,
  private val rxSchedulerProvider: RxSchedulerProvider
) : ViewModel() {
  private val initial = ViewState.initial()

  private val _stateD = MutableLiveData<ViewState>().apply { value = initial }
  private val stateS = BehaviorSubject.createDefault(initial)
  private val stateObservable get() = stateS.asObservable()

  private val intentS = PublishSubject.create<ViewIntent>()
  private val singleEventS = PublishSubject.create<SingleEvent>()
  private val compositeDisposable = CompositeDisposable()

  /// Expose view state live data & single event observable
  val stateD get() = _stateD.distinctUntilChanged()
  val singleEventObservable get() = singleEventS.asObservable()

  fun processIntents(intents: Observable<ViewIntent>) = intents.subscribe(intentS::onNext)!!

  private val initialProcessor =
    ObservableTransformer<ViewIntent.Initial, PartialStateChange> { intents ->
      intents
        .withLatestFrom(stateObservable)
        .filter { (_, vs) -> vs.photoItems.isEmpty() }
        .flatMap {
          Observable.mergeArray(
            interactor.photoFirstPageChanges(limit = PHOTO_PAGE_SIZE),
            interactor.postFirstPageChanges(limit = POST_PAGE_SIZE)
          )
        }
    }

  private val nextPageProcessor =
    ObservableTransformer<ViewIntent.LoadNextPage, PartialStateChange> { intents ->
      intents
        .withLatestFrom(stateObservable)
        .filter { (_, vs) -> vs.canLoadNextPage() }
        .map { (_, vs) -> vs.photoItems.size }
        .exhaustMap { interactor.photoNextPageChanges(start = it, limit = PHOTO_PAGE_SIZE) }
    }

  private val retryLoadPageProcessor =
    ObservableTransformer<ViewIntent.RetryLoadPage, PartialStateChange> { intents ->
      intents
        .withLatestFrom(stateObservable)
        .filter { (_, vs) -> vs.shouldRetry() }
        .map { (_, vs) -> vs.photoItems.size }
        .exhaustMap { interactor.photoNextPageChanges(start = it, limit = PHOTO_PAGE_SIZE) }
    }

  private val loadNextPageHorizontalProcessor =
    ObservableTransformer<ViewIntent.LoadNextPageHorizontal, PartialStateChange> { intents ->
      intents
        .withLatestFrom(stateObservable)
        .filter { (_, vs) -> vs.canLoadNextPageHorizontal() }
        .map { (_, vs) -> vs.getHorizontalListCount() }
        .exhaustMap { interactor.postNextPageChanges(start = it, limit = POST_PAGE_SIZE) }
    }

  private val retryLoadPageHorizontalProcessor =
    ObservableTransformer<ViewIntent.RetryLoadPageHorizontal, PartialStateChange> { intents ->
      intents
        .withLatestFrom(stateObservable)
        .filter { (_, vs) -> vs.shouldRetryNextPageHorizontal() }
        .map { (_, vs) -> vs.getHorizontalListCount() }
        .exhaustMap { interactor.postNextPageChanges(start = it, limit = POST_PAGE_SIZE) }
    }

  private val retryHorizontalProcessor =
    ObservableTransformer<ViewIntent.RetryHorizontal, PartialStateChange> { intents ->
      intents
        .withLatestFrom(stateObservable)
        .filter { (_, vs) -> vs.shouldRetryHorizontal() }
        .exhaustMap { interactor.postFirstPageChanges(limit = POST_PAGE_SIZE) }
    }

  private val refreshProcessor =
    ObservableTransformer<ViewIntent.Refresh, PartialStateChange> { intents ->
      intents
        .withLatestFrom(stateObservable)
        .filter { (_, vs) -> vs.enableRefresh }
        .exhaustMap {
          interactor.refreshAll(
            limitPhoto = PHOTO_PAGE_SIZE,
            limitPost = POST_PAGE_SIZE
          )
        }
    }

  private val toPartialStateChange =
    ObservableTransformer<ViewIntent, PartialStateChange> { intents ->
      intents
        .publish { shared ->
          Observable.mergeArray(
            shared.ofType<ViewIntent.Initial>().compose(initialProcessor),
            shared.ofType<ViewIntent.LoadNextPage>().compose(nextPageProcessor),
            shared.ofType<ViewIntent.RetryLoadPage>().compose(retryLoadPageProcessor),
            shared.ofType<ViewIntent.LoadNextPageHorizontal>().compose(
              loadNextPageHorizontalProcessor
            ),
            shared.ofType<ViewIntent.RetryLoadPageHorizontal>().compose(
              retryLoadPageHorizontalProcessor
            ),
            shared.ofType<ViewIntent.RetryHorizontal>().compose(retryHorizontalProcessor),
            shared.ofType<ViewIntent.Refresh>().compose(refreshProcessor)
          )
        }
        .compose(sendSingleEvent)
    }

  private val sendSingleEvent =
    ObservableTransformer<PartialStateChange, PartialStateChange> { changes ->
      changes
        .observeOn(rxSchedulerProvider.main)
        .doOnNext { change ->
          when (change) {
            is PhotoFirstPage.Data -> if (change.photos.isEmpty()) singleEventS.onNext(SingleEvent.HasReachedMax)
            is PhotoFirstPage.Error -> singleEventS.onNext(SingleEvent.GetPhotosFailure(change.error))
            PhotoFirstPage.Loading -> Unit
            ///
            is PhotoNextPage.Data -> if (change.photos.isEmpty()) singleEventS.onNext(SingleEvent.HasReachedMax)
            is PhotoNextPage.Error -> singleEventS.onNext(SingleEvent.GetPhotosFailure(change.error))
            PhotoNextPage.Loading -> Unit
            ///
            is PostFirstPage.Data -> if (change.posts.isEmpty()) singleEventS.onNext(SingleEvent.HasReachedMaxHorizontal)
            is PostFirstPage.Error -> singleEventS.onNext(SingleEvent.GetPostsFailure(change.error))
            PostFirstPage.Loading -> Unit
            ///
            is PostNextPage.Data -> if (change.posts.isEmpty()) singleEventS.onNext(SingleEvent.HasReachedMaxHorizontal)
            is PostNextPage.Error -> singleEventS.onNext(SingleEvent.GetPostsFailure(change.error))
            PostNextPage.Loading -> Unit
            ///
            is Refresh.Success -> singleEventS.onNext(SingleEvent.RefreshSuccess)
            is Refresh.Error -> singleEventS.onNext(SingleEvent.RefreshFailure(change.error))
            Refresh.Refreshing -> Unit
          }
        }
    }

  init {
    stateS
      .subscribeBy { _stateD.value = it }
      .addTo(compositeDisposable)

    intentS
      .compose(intentFilter)
      .compose(toPartialStateChange)
      .observeOn(rxSchedulerProvider.main)
      .scan(initial) { vs, change -> change.reduce(vs) }
      .subscribe(stateS::onNext)
      .addTo(compositeDisposable)
  }

  override fun onCleared() {
    super.onCleared()
    compositeDisposable.dispose()
  }

  private companion object {
    val intentFilter = ObservableTransformer<ViewIntent, ViewIntent> { intents ->
      intents.publish { shared ->
        Observable.mergeArray(
          shared.ofType<ViewIntent.Initial>().take(1),
          shared.filter { it !is ViewIntent.Initial }
        )
      }
    }
    const val PHOTO_PAGE_SIZE = 20
    const val POST_PAGE_SIZE = 10
  }
}

