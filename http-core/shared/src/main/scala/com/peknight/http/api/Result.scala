package com.peknight.http.api

import cats.data.Ior
import cats.syntax.option.*
import cats.{Id, Monad}
import com.peknight.api.codec.instances.pagination.given
import com.peknight.api.codec.instances.result.codecResult as apiCodecResult
import com.peknight.api.pagination.Pagination
import com.peknight.codec.circe.iso.codec
import com.peknight.codec.circe.sum.jsonType.given
import com.peknight.codec.cursor.Cursor
import com.peknight.codec.sum.*
import com.peknight.codec.{Codec, Decoder, Encoder}
import com.peknight.http.error.std.Problem
import com.peknight.http.syntax.error.toProblem
import io.circe.{Json, JsonObject}

case class Result[A](ior: Problem Ior A, override val pagination: Option[Pagination] = None)
  extends com.peknight.api.Result[Pagination, A]:
end Result
object Result:
  given codecResult[F[_], S, A](using Monad[F], ObjectType[S], NullType[S], ArrayType[S], BooleanType[S], NumberType[S],
                                StringType[S], Encoder[F, S, JsonObject], Decoder[F, Cursor[S], JsonObject],
                                Encoder[F, S, A], Decoder[F, Cursor[S], A])
  : Codec[F, S, Cursor[S], Result[A]] =
    apiCodecResult[F, S, Pagination, Problem, A, Result[A]]()(_.toProblem)(Result[A].apply)
  given jsonCodecResult[F[_], A](using Monad[F], Encoder[F, Json, A], Decoder[F, Cursor[Json], A])
  : Codec[F, Json, Cursor[Json], Result[A]] =
    codecResult[F, Json, A]
  given circeCodecResult[A](using Encoder[Id, Json, A], Decoder[Id, Cursor[Json], A]): io.circe.Codec[Result[A]] =
    codec[Result[A]]
end Result
