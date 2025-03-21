package org.anikonorov.rec.system.be

import zio._
import org.neo4j.driver.AuthTokens
import neotypes.zio.implicits._
import neotypes.zio.stream.ZioStream
import neotypes.zio.stream.implicits._
import org.anikonorov.rec.system.be.api.RoutesImpl
import org.anikonorov.rec.system.be.service.Neo4jServiceImpl
import zio.http.Server

object MainApp extends ZIOAppDefault {

  val driver: ZLayer[Any, Throwable, neotypes.StreamDriver[ZioStream, Task]] =
    ZLayer.scoped(neotypes.GraphDatabase.streamDriver[ZioStream]("bolt://localhost:7687", AuthTokens.basic("neo4j", "12345678")))

  override def run = {
    (for {
      routes <- ZIO.serviceWith[RoutesImpl](_.routes)
      _      <- zio.http.Server.serve(routes).provide(Server.default)
    } yield ()).provide(driver, Neo4jServiceImpl.layer, RoutesImpl.layer)
  }
}
