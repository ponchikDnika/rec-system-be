package org.anikonorov.rec.system.be

import zio._
import org.neo4j.driver.AuthTokens
import neotypes.zio.implicits._
import neotypes.zio.stream.ZioStream
import neotypes.zio.stream.implicits._
import org.anikonorov.rec.system.be.api.RoutesImpl
import org.anikonorov.rec.system.be.service.Neo4jServiceImpl
import zio.http.Header.{AccessControlAllowOrigin, Origin}
import zio.http.Middleware.{CorsConfig, cors}
import zio.http.{Middleware, Server}

object MainApp extends ZIOAppDefault {

  val config: CorsConfig =
    CorsConfig(
      allowedOrigin = {
        case origin if origin == Origin.parse("http://localhost:3000").toOption.get =>
          Some(AccessControlAllowOrigin.Specific(origin))
        case _ => None
      }
    )

  val driver: ZLayer[Any, Throwable, neotypes.StreamDriver[ZioStream, Task]] =
    ZLayer.scoped(neotypes.GraphDatabase.streamDriver[ZioStream]("bolt://localhost:7687", AuthTokens.basic("neo4j", "12345678")))

  override def run = {
    (for {
      routes <- ZIO.serviceWith[RoutesImpl](_.routes)
      _      <- zio.http.Server.serve(routes @@ cors(config) @@ Middleware.requestLogging(logRequestBody = true, logResponseBody = true)).provide(Server.default)
    } yield ()).provide(driver, Neo4jServiceImpl.layer, RoutesImpl.layer)
  }
}
