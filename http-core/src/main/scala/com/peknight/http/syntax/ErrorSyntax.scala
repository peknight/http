package com.peknight.http.syntax

import com.peknight.error.Error
import cats.syntax.option.*
import com.peknight.http.error.std.Problem
import org.http4s.Uri

trait ErrorSyntax:
  extension [E] (error: E)
    def toProblem: Problem =
      Error(error) match
        case problem: Problem => problem
        case error =>
          Problem(Uri.unsafeFromString(s"urn:ietf:peknight:error:${error.errorType}"),
            error.message.some, none, error.showValue)
  end extension
end ErrorSyntax
object ErrorSyntax extends ErrorSyntax
