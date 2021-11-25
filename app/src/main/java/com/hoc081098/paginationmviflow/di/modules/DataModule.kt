package com.hoc081098.paginationmviflow.di.modules

import com.hoc081098.paginationmviflow.BuildConfig
import com.hoc081098.paginationmviflow.data.remote.ApiService
import com.hoc081098.paginationmviflow.data.remote.BASE_URL
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DataModule {
  @Singleton
  @Provides
  fun provideApiService(retrofit: Retrofit): ApiService {
    return ApiService(retrofit)
  }

  @Singleton
  @Provides
  fun provideRetrofit(client: OkHttpClient): Retrofit {
    return Retrofit.Builder()
      .baseUrl(BASE_URL)
      .client(client)
      .addConverterFactory(
        MoshiConverterFactory.create(
          Moshi
            .Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        )
      )
      .build()
  }

  @Singleton
  @Provides
  fun provideOkHttpClient(): OkHttpClient {
    return OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .writeTimeout(15, TimeUnit.SECONDS)
      .apply {
        if (BuildConfig.DEBUG) {
          HttpLoggingInterceptor()
            .setLevel(HttpLoggingInterceptor.Level.BODY)
            .let(::addInterceptor)
        }
      }
      .build()
  }
}
