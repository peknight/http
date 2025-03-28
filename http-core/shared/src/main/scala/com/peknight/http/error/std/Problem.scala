package com.peknight.http.error.std

import cats.Monad
import com.peknight.codec.circe.iso.codec
import com.peknight.codec.circe.sum.jsonType.given
import com.peknight.codec.configuration.CodecConfiguration
import com.peknight.codec.cursor.Cursor
import com.peknight.codec.http4s.instances.status.given
import com.peknight.codec.http4s.instances.uri.given
import com.peknight.codec.sum.*
import com.peknight.codec.{Codec, Decoder, Encoder}
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
  given codecProblem[F[_], S](using Monad[F], ObjectType[S], NullType[S], ArrayType[S], BooleanType[S], NumberType[S],
                              StringType[S], Encoder[F, S, JsonObject], Decoder[F, Cursor[S], JsonObject])
  : Codec[F, S, Cursor[S], Problem] =
    given CodecConfiguration = CodecConfiguration.default.withExtField("ext")
    Codec.derived[F, S, Problem]
  given jsonCodecProblem[F[_]: Monad]: Codec[F, Json, Cursor[Json], Problem] =
    codecProblem[F, Json]
  given circeCodecProblem: io.circe.Codec[Problem] = codec[Problem]
end Problem
