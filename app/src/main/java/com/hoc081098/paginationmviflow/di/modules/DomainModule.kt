package com.hoc081098.paginationmviflow.di.modules

import com.hoc081098.paginationmviflow.data.PhotoRepositoryImpl
import com.hoc081098.paginationmviflow.data.PostRepositoryImpl
import com.hoc081098.paginationmviflow.domain.dispatchers_schedulers.CoroutinesDispatchersProvider
import com.hoc081098.paginationmviflow.domain.dispatchers_schedulers.CoroutinesDispatchersProviderImpl
import com.hoc081098.paginationmviflow.domain.repository.PhotoRepository
import com.hoc081098.paginationmviflow.domain.repository.PostRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent

@Module
@InstallIn(ApplicationComponent::class)
interface DomainModule {
  @Binds
  fun provideCoroutinesDispatchersProvider(coroutinesDispatchersProviderImpl: CoroutinesDispatchersProviderImpl): CoroutinesDispatchersProvider

  @Binds
  fun providePhotoRepository(photoRepositoryImpl: PhotoRepositoryImpl): PhotoRepository

  @Binds
  fun providePostRepository(postRepositoryImpl: PostRepositoryImpl): PostRepository
}