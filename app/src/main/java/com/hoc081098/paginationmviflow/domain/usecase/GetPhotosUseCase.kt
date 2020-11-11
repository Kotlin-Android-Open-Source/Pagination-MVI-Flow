package com.hoc081098.paginationmviflow.domain.usecase

import com.hoc081098.paginationmviflow.domain.entity.Photo
import com.hoc081098.paginationmviflow.domain.repository.PhotoRepository
import javax.inject.Inject

class GetPhotosUseCase @Inject constructor(
  private val photoRepository: PhotoRepository
) {
  suspend operator fun invoke(start: Int, limit: Int): List<Photo> {
    return photoRepository.getPhotos(start = start, limit = limit)
  }
}