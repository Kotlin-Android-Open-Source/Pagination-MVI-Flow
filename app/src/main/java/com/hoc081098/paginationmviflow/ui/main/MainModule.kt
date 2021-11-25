package com.hoc081098.paginationmviflow.ui.main

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent

@Module
@InstallIn(ActivityRetainedComponent::class)
interface MainModule {
  @Binds
  fun provideMainInteractor(impl: MainInteractorImpl): MainContract.Interactor
}
