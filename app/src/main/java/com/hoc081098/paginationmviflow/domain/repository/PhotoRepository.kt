package com.hoc081098.paginationmviflow.domain.repository

import com.hoc081098.paginationmviflow.domain.entity.Photo

interface PhotoRepository {
  suspend fun getPhotos(
    start: Int,
    limit: Int
  ): List<Photo>
}