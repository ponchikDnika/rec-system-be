package org.anikonorov.rec.system.be.api

import io.circe.Encoder
import io.circe.syntax.EncoderOps
import org.anikonorov.rec.system.be.service.Neo4jService
import zio.{ZIO, ZLayer}
import zio.http._
import zio.stream.{ZPipeline, ZStream}

case class RoutesImpl(neo4jService: Neo4jService) {

  private def streamingResponse[T: Encoder](stream: ZStream[Any, Throwable, T]): Response = {
    val data =
      ZStream("""{"data": """) ++
        (ZStream("[") ++ stream.map(_.asJson.noSpaces).intersperse(",") ++ ZStream("]")) ++
        ZStream("""}""")
    val body = Body.fromStreamChunked(data.via(ZPipeline.utf8Encode))
    Response(body = body).contentType(MediaType.application.json)
  }

  private def getSimilarMovies(movieId: Long) = {
    for {
      similar <- neo4jService.getSimilarMovies(movieId).runCollect
      res     <- ZIO.when(similar.isEmpty)(neo4jService.getRandomMovies(10).runCollect)
    } yield Response.json(res.getOrElse(similar).asJson.noSpaces)
  }

  val routes = Routes(
    Method.GET / "movies" -> handler { (req: Request) =>
      streamingResponse(neo4jService.getRandomMovies())
    },
    Method.GET / "movie" / int("movieId") -> handler { (movieId: Int, req: Request) =>
      neo4jService.getMovieById(movieId).map {
        case Some(movie) => Response.json(movie.asJson.noSpaces)
        case None        => Response.status(Status.NotFound)
      }
    },
    Method.GET / "recommendations" / "similar" / long("movieId") -> handler { (movieId: Long, req: Request) =>
      getSimilarMovies(movieId)
    },
    Method.GET / "recommendations" / "tag" / string("tag") -> handler { (tag: String, req: Request) =>
      streamingResponse(neo4jService.getRecommendedMoviesByTag(tag))
    },
    Method.GET / "recommendations" / "user" / long("userId") -> handler { (userId: Long, req: Request) =>
      streamingResponse(neo4jService.getRecommendedMoviesForUser(userId))
    },
    Method.GET / "movie" / "details" / string("externalId") -> handler { (externalId: String, req: Request) =>
      neo4jService.getMovieDetailsByExternalId(externalId).map {
        case Some(movie) => Response.json(movie.asJson.noSpaces)
        case None        => Response.status(Status.NotFound)
      }
    }
  ).handleErrorCauseZIO { c =>
    ZIO.succeed(Response.internalServerError(c.prettyPrint))
  }
}

object RoutesImpl {
  val layer = ZLayer.fromFunction(RoutesImpl.apply _)
}
