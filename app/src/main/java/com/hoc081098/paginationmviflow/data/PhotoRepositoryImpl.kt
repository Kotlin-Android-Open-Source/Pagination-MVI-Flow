package com.hoc081098.paginationmviflow.data

import com.hoc081098.paginationmviflow.data.remote.ApiService
import com.hoc081098.paginationmviflow.domain.dispatchers_schedulers.CoroutinesDispatchersProvider
import com.hoc081098.paginationmviflow.domain.entity.Photo
import com.hoc081098.paginationmviflow.domain.repository.PhotoRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val dispatchersProvider: CoroutinesDispatchersProvider
) : PhotoRepository {
  override suspend fun getPhotos(start: Int, limit: Int): List<Photo> {
    return withContext(dispatchersProvider.io) {
      apiService.getPhotos(start = start, limit = limit).map {
        Photo(
            id = it.id,
            title = it.title,
            albumId = it.albumId,
            thumbnailUrl = it.thumbnailUrl,
            url = it.url
        )
      }
    }
  }
}