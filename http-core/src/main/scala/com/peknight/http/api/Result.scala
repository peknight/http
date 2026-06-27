package com.peknight.http.api

import cats.data.Ior
import cats.{Id, Monad, Show}
import com.peknight.api.codec.instances.pagination.given
import com.peknight.api.codec.instances.result.{decodeResult as apiDecodeResult, encodeResult as apiEncodeResult}
import com.peknight.api.pagination.Pagination
import com.peknight.codec.circe.iso.codec
import com.peknight.codec.circe.sum.jsonType.given
import com.peknight.codec.cursor.Cursor
import com.peknight.codec.sum.*
import com.peknight.codec.{Decoder, Encoder}
import com.peknight.http.error.std.Problem
import com.peknight.http.syntax.error.toProblem
import io.circe.{Json, JsonObject}

case class Result[A](ior: Problem Ior A, override val pagination: Option[Pagination] = None)
  extends com.peknight.api.Result[Pagination, A]:
end Result
object Result:
  given encodeResult[F[_], S, A](using Monad[F], ObjectType[S], NullType[S], ArrayType[S], BooleanType[S], NumberType[S],
                                 StringType[S], Encoder[F, S, JsonObject], Encoder[F, S, A])
  : Encoder[F, S, Result[A]] =
    apiEncodeResult[F, S, Pagination, Problem, A, Result[A]]()(_.toProblem)

  given decodeResult[F[_], S, A](using Monad[F], ObjectType[S], NullType[S], ArrayType[S], BooleanType[S], NumberType[S],
                                 StringType[S], Decoder[F, Cursor[S], JsonObject], Decoder[F, Cursor[S], A], Show[S])
  : Decoder[F, Cursor[S], Result[A]] =
    apiDecodeResult[F, S, Pagination, Problem, A, Result[A]]()(_.toProblem)(Result[A].apply)

  given jsonEncodeResult[F[_], A](using Monad[F], Encoder[F, Json, A]): Encoder[F, Json, Result[A]] =
    encodeResult[F, Json, A]

  given jsonDecoderResult[F[_], A](using Monad[F], Decoder[F, Cursor[Json], A]): Decoder[F, Cursor[Json], Result[A]] =
    decodeResult[F, Json, A]
  given circeCodecResult[A](using Encoder[Id, Json, A], Decoder[Id, Cursor[Json], A]): io.circe.Codec[Result[A]] =
    codec[Result[A]]
end Result
