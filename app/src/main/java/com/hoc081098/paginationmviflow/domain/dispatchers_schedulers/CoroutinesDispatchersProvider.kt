package com.hoc081098.paginationmviflow.domain.dispatchers_schedulers

import kotlinx.coroutines.CoroutineDispatcher

interface CoroutinesDispatchersProvider {
  val io: CoroutineDispatcher
  val main: CoroutineDispatcher
}