package com.hoc081098.paginationmviflow.data

import com.hoc081098.paginationmviflow.data.remote.ApiService
import com.hoc081098.paginationmviflow.domain.dispatchers_schedulers.CoroutinesDispatchersProvider
import com.hoc081098.paginationmviflow.domain.entity.Post
import com.hoc081098.paginationmviflow.domain.repository.PostRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val dispatchersProvider: CoroutinesDispatchersProvider
) : PostRepository {
  override suspend fun getPosts(start: Int, limit: Int): List<Post> {
    return withContext(dispatchersProvider.io) {
      apiService.getPosts(start = start, limit = limit).map {
        Post(
            body = it.body,
            title = it.title,
            id = it.id,
            userId = it.userId
        )
      }
    }
  }

}