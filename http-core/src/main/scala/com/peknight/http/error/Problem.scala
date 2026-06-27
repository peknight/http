package com.peknight.http.error

import com.peknight.codec.circe.Ext
import com.peknight.error.Error
import org.http4s.{Status, Uri}

/**
 * RFC7807
 */
trait Problem extends Error with Ext:
  def `type`: Uri
  def title: Option[String]
  def status: Option[Status]
  def detail: Option[String]
  def instance: Option[Uri]
end Problem
