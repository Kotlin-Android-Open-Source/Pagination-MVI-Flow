package com.hoc081098.paginationmviflow.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoc081098.paginationmviflow.FlowTransformer
import com.hoc081098.paginationmviflow.pipe
import com.hoc081098.paginationmviflow.ui.main.MainContract.PartialStateChange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
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

@HiltViewModel
class MainVM @Inject constructor(
  private val mainProcessors: MainProcessors,
) : ViewModel() {
  private val initialVS = VS.initial()

  private val _stateFlow = MutableStateFlow(initialVS)
  private val _singleEventChannel = Channel<SE>(Channel.UNLIMITED)
  private val _intentFlow = MutableSharedFlow<VI>(extraBufferCapacity = 64)

  val stateFlow: StateFlow<VS> get() = _stateFlow.asStateFlow()
  val singleEventFlow: Flow<SE> get() = _singleEventChannel.receiveAsFlow()

  suspend fun processIntent(intent: VI) = _intentFlow.emit(intent)

  private val toPartialStateChanges: FlowTransformer<VI, PartialStateChange> =
    FlowTransformer { intents ->
      intents
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed())
        .let { shared ->
          merge(
            shared.filterIsInstance<VI.Initial>()
              .pipe(mainProcessors.getInitialProcessor(stateFlow)),
            shared.filterIsInstance<VI.LoadNextPage>()
              .pipe(mainProcessors.getNextPageProcessor(stateFlow)),
            shared.filterIsInstance<VI.RetryLoadPage>()
              .pipe(mainProcessors.getRetryLoadPageProcessor(stateFlow)),
            shared.filterIsInstance<VI.LoadNextPageHorizontal>()
              .pipe(mainProcessors.getLoadNextPageHorizontalProcessor(stateFlow)),
            shared.filterIsInstance<VI.RetryLoadPageHorizontal>()
              .pipe(mainProcessors.getRetryLoadPageHorizontalProcessor(stateFlow)),
            shared.filterIsInstance<VI.RetryHorizontal>()
              .pipe(mainProcessors.getRetryHorizontalProcessor(stateFlow)),
            shared.filterIsInstance<VI.Refresh>()
              .pipe(mainProcessors.getRefreshProcessor(stateFlow))
          )
        }
    }

  private val sendSingleEvent: FlowTransformer<PartialStateChange, PartialStateChange> =
    FlowTransformer { changes ->
      changes.onEach {
        _singleEventChannel.send(it.toEvent() ?: return@onEach)
      }
    }

  init {
    _intentFlow
      .pipe(intentFilterer)
      .pipe(toPartialStateChanges)
      .pipe(sendSingleEvent)
      .scan(initialVS) { vs, change -> change.reduce(vs) }
      .onEach { _stateFlow.value = it }
      .launchIn(viewModelScope)
  }

  internal companion object {
    val intentFilterer: FlowTransformer<VI, VI> = FlowTransformer { intents ->
      merge(
        intents.filterIsInstance<VI.Initial>().take(1),
        intents.filter { it !is VI.Initial }
      )
    }

    const val PHOTO_PAGE_SIZE = 20
    const val POST_PAGE_SIZE = 10
  }
}
