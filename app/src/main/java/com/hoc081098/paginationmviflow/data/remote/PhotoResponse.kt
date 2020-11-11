package com.hoc081098.paginationmviflow.data.remote

import com.squareup.moshi.Json

data class PhotoResponse(
  @Json(name = "albumId")
  val albumId: Int, // 1
  @Json(name = "id")
  val id: Int, // 5
  @Json(name = "thumbnailUrl")
  val thumbnailUrl: String, // https://via.placeholder.com/150/f66b97
  @Json(name = "title")
  val title: String, // natus nisi omnis corporis facere molestiae rerum in
  @Json(name = "url")
  val url: String // https://via.placeholder.com/600/f66b97
)