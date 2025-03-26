package com.peknight.http.media

import org.http4s.MediaType

object MediaRange:
  val `application/problem+json`: MediaType = MediaType.unsafeParse("application/problem+json")
end MediaRange
