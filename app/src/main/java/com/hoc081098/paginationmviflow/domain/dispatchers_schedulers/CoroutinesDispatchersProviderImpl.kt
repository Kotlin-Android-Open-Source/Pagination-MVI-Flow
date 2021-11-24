package com.hoc081098.paginationmviflow.domain.dispatchers_schedulers

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Singleton
class CoroutinesDispatchersProviderImpl @Inject constructor() : CoroutinesDispatchersProvider {
  override val io: CoroutineDispatcher = Dispatchers.IO
  override val main: CoroutineDispatcher = Dispatchers.Main
}
