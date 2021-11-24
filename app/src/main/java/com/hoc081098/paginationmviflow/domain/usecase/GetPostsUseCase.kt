package com.hoc081098.paginationmviflow.domain.usecase

import com.hoc081098.paginationmviflow.domain.entity.Post
import com.hoc081098.paginationmviflow.domain.repository.PostRepository
import javax.inject.Inject

class GetPostsUseCase @Inject constructor(
  private val postRepository: PostRepository
) {
  suspend operator fun invoke(start: Int, limit: Int): List<Post> {
    return postRepository.getPosts(start = start, limit = limit)
  }
}
