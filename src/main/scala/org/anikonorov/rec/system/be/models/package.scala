package org.anikonorov.rec.system.be

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

package object models {
  final case class Movie(movieId: Long, title: String, genres: List[String], imdbId: Option[String], tmdbId: Option[String], tags: List[String])
  object Movie {
    implicit val encoder: Encoder[Movie] = deriveEncoder
  }

//  final case class MovieWithTags(movie: Movie, tags: List[String])
//  object MovieWithTags {
//    implicit val encoder: Encoder[MovieWithTags] = deriveEncoder
//  }



  final case class Rating(userId: Long, movieId: Long, rating: Double)
  final case class Tag(userId: Long, movieId: Long, tag: String)
}
