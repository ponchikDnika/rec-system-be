package org.anikonorov.rec.system.be

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

package object models {
  final case class Movie(
      movieId: Long,
      title: String,
      genres: List[String],
      imdbId: Option[String],
      tmdbId: Option[String],
      tags: List[String]
  )
  object Movie {
    implicit val encoder: Encoder[Movie] = deriveEncoder
  }

  case class Pagination(page: Int, limit: Int)

  object Pagination {
    def safeApply(page: Int, limit: Int): Pagination = {
      Option
        .when(page >= 0 && limit > 0 && page < 100 && limit < 100)(Pagination(page, limit))
        .getOrElse(Pagination(1, 10))
    }
  }
}
