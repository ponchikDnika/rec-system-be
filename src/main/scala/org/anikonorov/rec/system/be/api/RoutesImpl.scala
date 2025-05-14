package org.anikonorov.rec.system.be.api

import io.circe.Encoder
import io.circe.syntax.EncoderOps
import org.anikonorov.rec.system.be.models.Pagination
import org.anikonorov.rec.system.be.service.Neo4jService
import zio.http._
import zio.stream.{ZPipeline, ZStream}
import zio.{ZIO, ZLayer}

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

case class RoutesImpl(neo4jService: Neo4jService) {

  val routes = Routes(
    Method.GET / "movies" / int("movieId") -> handler { (movieId: Int, req: Request) =>
      neo4jService.getMovieById(movieId).map {
        case Some(movie) => Response.json(movie.asJson.noSpaces)
        case None        => Response.status(Status.NotFound)
      }
    },
    Method.GET / "movies" -> handler { (req: Request) =>
      streamingResponse(neo4jService.getRandomMovies(getPagination(req)))
    },
    Method.GET / "movies" / "details" / string("externalId") -> handler { (externalId: String, req: Request) =>
      neo4jService.getMovieDetailsByExternalId(externalId).map {
        case Some(movie) => Response.json(movie.asJson.noSpaces)
        case None        => Response.status(Status.NotFound)
      }
    },
    Method.GET / "movies" / "search" -> handler { (req: Request) =>
      req.query[String]("searchStr") match {
        case Left(_)                                => Response.status(Status.NotFound)
        case Right(searchStr) if searchStr.nonEmpty => streamingResponse(neo4jService.getMoviesByTitle(searchStr, getPagination(req)))
      }
    },
    Method.GET / "recommendations" / "similar" / long("movieId") -> handler { (movieId: Long, req: Request) =>
      getSimilarMovies(movieId, getPagination(req))
    },
    Method.GET / "recommendations" / "tag" / string("tag") -> handler { (tag: String, req: Request) =>
      streamingResponse(neo4jService.getRecommendedMoviesByTag(URLDecoder.decode(tag, StandardCharsets.UTF_8), getPagination(req)))
    },
    Method.GET / "recommendations" / "genre" / string("genre") -> handler { (genre: String, req: Request) =>
      streamingResponse(neo4jService.getRecommendedMoviesByGenre(URLDecoder.decode(genre, StandardCharsets.UTF_8), getPagination(req)))
    }
  ).handleErrorCauseZIO { c =>
    ZIO.succeed(Response.internalServerError(c.prettyPrint))
  }

  private def streamingResponse[T: Encoder](stream: ZStream[Any, Throwable, T]): Response = {
    val data =
      ZStream("""{"data": """) ++
        (ZStream("[") ++ stream.map(_.asJson.noSpaces).intersperse(",") ++ ZStream("]")) ++
        ZStream("""}""")
    val body = Body.fromStreamChunked(data.via(ZPipeline.utf8Encode))
    Response(body = body).contentType(MediaType.application.json)
  }

  private def getSimilarMovies(movieId: Long, pagination: Pagination) = {
    for {
      similar <- neo4jService.getSimilarMovies(movieId, pagination).runCollect
      res     <- ZIO.when(similar.isEmpty)(neo4jService.getRandomMovies(pagination).runCollect)
    } yield streamingResponse(ZStream.fromChunk(res.getOrElse(similar)))
  }

  private def getPagination(req: Request) = {
    val page  = req.query[Int]("page").getOrElse(1)
    val limit = req.query[Int]("limit").getOrElse(12)
    Pagination.safeApply(page, limit)
  }
}

object RoutesImpl {
  val layer = ZLayer.fromFunction(RoutesImpl.apply _)
}
