package com.hoc081098.paginationmviflow.domain.repository

import com.hoc081098.paginationmviflow.domain.entity.Post

interface PostRepository {
  suspend fun getPosts(
    start: Int,
    limit: Int
  ): List<Post>
}