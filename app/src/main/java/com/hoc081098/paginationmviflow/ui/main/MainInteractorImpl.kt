package com.hoc081098.paginationmviflow.ui.main

import android.util.Log
import com.hoc081098.paginationmviflow.domain.entity.Photo
import com.hoc081098.paginationmviflow.domain.entity.Post
import com.hoc081098.paginationmviflow.domain.usecase.GetPhotosUseCase
import com.hoc081098.paginationmviflow.domain.usecase.GetPostsUseCase
import com.hoc081098.paginationmviflow.ui.main.MainContract.PartialStateChange.PhotoFirstPage
import com.hoc081098.paginationmviflow.ui.main.MainContract.PartialStateChange.PhotoNextPage
import com.hoc081098.paginationmviflow.ui.main.MainContract.PartialStateChange.PostFirstPage
import com.hoc081098.paginationmviflow.ui.main.MainContract.PartialStateChange.PostNextPage
import com.hoc081098.paginationmviflow.ui.main.MainContract.PartialStateChange.Refresh
import com.hoc081098.paginationmviflow.ui.main.MainContract.PhotoVS
import com.hoc081098.paginationmviflow.ui.main.MainContract.PostVS
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

@Suppress("USELESS_CAST")
class MainInteractorImpl @Inject constructor(
  private val getPhotosUseCase: GetPhotosUseCase,
  private val getPostsUseCase: GetPostsUseCase,
) : MainContract.Interactor {
  init {
    Log.d("###", toString())
  }

  override fun photoNextPageChanges(
    start: Int,
    limit: Int
  ): Flow<PhotoNextPage> = flow { emit(getPhotosUseCase(start = start, limit = limit)) }
    .map { photos ->
      photos
        .map(MainContract::PhotoVS)
        .let { PhotoNextPage.Data(it) } as PhotoNextPage
    }
    .onStart { emit(PhotoNextPage.Loading) }
    .catch { emit(PhotoNextPage.Error(it)) }

  override fun photoFirstPageChanges(limit: Int): Flow<PhotoFirstPage> =
    flow { emit(getPhotosUseCase(start = 0, limit = limit)) }
      .map { photos ->
        photos.map(::PhotoVS)
          .let { PhotoFirstPage.Data(it) } as PhotoFirstPage
      }
      .onStart { emit(PhotoFirstPage.Loading) }
      .catch { emit(PhotoFirstPage.Error(it)) }

  override fun postFirstPageChanges(limit: Int): Flow<PostFirstPage> =
    flow { emit(getPostsUseCase(start = 0, limit = limit)) }
      .map { posts ->
        posts.map(::PostVS)
          .let { PostFirstPage.Data(it) } as PostFirstPage
      }
      .onStart { emit(PostFirstPage.Loading) }
      .catch { emit(PostFirstPage.Error(it)) }

  override fun postNextPageChanges(
    start: Int,
    limit: Int
  ): Flow<PostNextPage> = flow { emit(getPostsUseCase(start = start, limit = limit)) }
    .map { posts ->
      posts.map(::PostVS)
        .let { PostNextPage.Data(it) } as PostNextPage
    }
    .onStart { emit(PostNextPage.Loading) }
    .catch { emit(PostNextPage.Error(it)) }

  override fun refreshAll(
    limitPost: Int,
    limitPhoto: Int
  ): Flow<Refresh> = flow {
    coroutineScope {
      val async1: Deferred<List<Post>> = async { getPostsUseCase(limit = limitPost, start = 0) }
      val async2: Deferred<List<Photo>> = async { getPhotosUseCase(limit = limitPhoto, start = 0) }
      val results = awaitAll(async1, async2)

      @Suppress("UNCHECKED_CAST")
      emit(
        Refresh.Success(
          posts = (results[0] as List<Post>).map(::PostVS),
          photos = (results[1] as List<Photo>).map(::PhotoVS)
        ) as Refresh
      )
    }
  }.onStart { emit(Refresh.Refreshing) }
    .catch { emit(Refresh.Error(it)) }
}
