package com.hoc081098.paginationmviflow.data.remote

import com.squareup.moshi.Json

data class PostResponse(
  @Json(name = "body")
  val body: String, // cupiditate quo est a modi nesciunt solutaipsa voluptas error itaque dicta inautem qui minus magnam et distinctio eumaccusamus ratione error aut
  @Json(name = "id")
  val id: Int, // 100
  @Json(name = "title")
  val title: String, // at nam consequatur ea labore ea harum
  @Json(name = "userId")
  val userId: Int // 10
)