package org.anikonorov.rec.system.be.api

import io.circe.Encoder
import io.circe.syntax.EncoderOps
import org.anikonorov.rec.system.be.service.Neo4jService
import zio.{ZIO, ZLayer}
import zio.http._
import zio.stream.{ZPipeline, ZStream}

case class RoutesImpl(neo4jService: Neo4jService) {

  private def streamingResponse[T: Encoder](stream: ZStream[Any, Throwable, T]): Response = {
    val _stream =
      (ZStream("[") ++ stream.map(_.asJson.noSpaces).intersperse(",") ++ ZStream("]")).via(ZPipeline.utf8Encode)
    val data = Body.fromStreamChunked(_stream)
    Response(body = data).contentType(MediaType.application.json)
  }

  val routes = Routes(
    Method.GET / "recommendations" / "user" / long("userId") -> handler { (userId: Long, req: Request) =>
      streamingResponse(neo4jService.getRecommendedMoviesForUser(userId))
    },
    Method.GET / "recommendations" / "tag" / string("tag") -> handler { (tag: String, req: Request) =>
      streamingResponse(neo4jService.getRecommendedMoviesByTag(tag))
    },
    Method.GET / "recommendations" / "similar" / long("movieId") -> handler { (movieId: Long, req: Request) =>
      streamingResponse(neo4jService.getSimilarMovies(movieId))
    },
    Method.GET / "movie" / "details" / string("externalId") -> handler { (externalId: String, req: Request) =>
      neo4jService.getMovieDetails(externalId).map {
        case Some(movie) => Response.json(movie.asJson.noSpaces)
        case None        => Response.status(Status.NotFound)
      }
    },
    Method.GET / "movies" / "random" -> handler { (req: Request) =>
      streamingResponse(neo4jService.getRandomMovies())
    }
  ).handleErrorCauseZIO { c =>
    ZIO.succeed(Response.internalServerError(c.prettyPrint))
  }
}

object RoutesImpl {
  val layer = ZLayer.fromFunction(RoutesImpl.apply _)
}
