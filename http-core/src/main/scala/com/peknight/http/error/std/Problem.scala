package com.peknight.http.error.std

import cats.{Monad, Show}
import com.peknight.codec.circe.iso.codec
import com.peknight.codec.circe.sum.jsonType.given
import com.peknight.codec.config.CodecConfig
import com.peknight.codec.cursor.Cursor
import com.peknight.codec.http4s.instances.status.given
import com.peknight.codec.http4s.instances.uri.given
import com.peknight.codec.sum.*
import com.peknight.codec.{Decoder, Encoder}
import io.circe.{Json, JsonObject}
import org.http4s.{Status, Uri}

case class Problem(
                    `type`: Uri,
                    title: Option[String] = None,
                    status: Option[Status] = None,
                    detail: Option[String] = None,
                    instance: Option[Uri] = None,
                    ext: JsonObject = JsonObject.empty
                  ) extends com.peknight.http.error.Problem
object Problem:
  given encodeProblem[F[_], S](using Monad[F], ObjectType[S], NullType[S], ArrayType[S], BooleanType[S], NumberType[S],
                               StringType[S], Encoder[F, S, JsonObject]): Encoder[F, S, Problem] =
    given CodecConfig = CodecConfig.default.withExtField("ext")
    Encoder.derived[F, S, Problem]
  given decodeProblem[F[_], S](using Monad[F], ObjectType[S], NullType[S], ArrayType[S], BooleanType[S], NumberType[S],
                               StringType[S], Decoder[F, Cursor[S], JsonObject], Show[S])
  : Decoder[F, Cursor[S], Problem] =
    given CodecConfig = CodecConfig.default.withExtField("ext")
    Decoder.derived[F, S, Problem]
  given jsonEncodeProblem[F[_]: Monad]: Encoder[F, Json, Problem] =
    encodeProblem[F, Json]
  given jsonDecodeProblem[F[_]: Monad]: Decoder[F, Cursor[Json], Problem] =
    decodeProblem[F, Json]
  given circeCodecProblem: io.circe.Codec[Problem] = codec[Problem]
end Problem
