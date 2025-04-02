package org.anikonorov.rec.system.be.service

import neotypes.StreamDriver
import neotypes.mappers.ResultMapper
import neotypes.syntax.all._
import neotypes.zio.stream.ZioStream
import org.anikonorov.rec.system.be.models.Movie
import zio.stream.ZStream
import zio.{Task, ZLayer}

trait Neo4jService {
  def getRecommendedMoviesForUser(userId: Long, limit: Int = Neo4jService.limit): ZStream[Any, Throwable, Movie]
  def getRecommendedMoviesByTag(tag: String, limit: Int = Neo4jService.limit): ZStream[Any, Throwable, Movie]
  def getSimilarMovies(movieId: Long, limit: Int = Neo4jService.limit): ZStream[Any, Throwable, Movie]
  def getMovieDetailsByExternalId(externalId: String): Task[Option[Movie]]
  def getRandomMovies(limit: Int = Neo4jService.limit): ZStream[Any, Throwable, Movie]
  def findMoviesByTitle(searchStr: String, limit: Int = Neo4jService.limit): ZStream[Any, Throwable, Movie]
  def getMovieById(movieId: Int): Task[Option[Movie]]
}

object Neo4jService {
  val limit = 10
}

case class Neo4jServiceImpl(driver: StreamDriver[ZioStream, Task]) extends Neo4jService {

  def getRecommendedMoviesForUser(userId: Long, limit: Int): ZStream[Any, Throwable, Movie] =
    c"""
      MATCH (u:User {userId: $userId})-[:RATED]->(m:Movie)<-[:RATED]-(other:User)-[:RATED]->(rec:Movie)
      OPTIONAL MATCH (rec)-[:HAS_LINK]->(link:Link)
      OPTIONAL MATCH (rec)-[:TAGGED]->(t:Tag)
      WITH rec, link, COLLECT(COALESCE(t.tag, '')) AS tags
      RETURN rec.movieId AS movieId, rec.title AS title, rec.genres AS genres,
             link.imdbId AS imdbId, link.tmdbId AS tmdbId, [tag IN tags WHERE tag <> ''] AS nonEmptyTags
      LIMIT $limit
    """
      .query(ResultMapper.fromFunction(Movie.apply _))
      .stream(driver)

  def getRecommendedMoviesByTag(tag: String, limit: Int): ZStream[Any, Throwable, Movie] =
    c"""
    MATCH (m:Movie)-[:TAGGED]->(t:Tag)
    WHERE toLower(t.tag) = toLower($tag)
    OPTIONAL MATCH (m)-[:HAS_LINK]->(link:Link)
    RETURN m.movieId AS movieId,
           m.title AS title,
           m.genres AS genres,
           link.imdbId AS imdbId,
           link.tmdbId AS tmdbId
    LIMIT $limit
  """
      .query(ResultMapper.fromFunction(Movie.apply _))
      .stream(driver)

  def getSimilarMovies(movieId: Long, limit: Int): ZStream[Any, Throwable, Movie] =
    c"""
      MATCH (m:Movie {movieId: $movieId})
      MATCH (m)-[s:SIMILAR]-(rec:Movie)
      OPTIONAL MATCH (rec)-[:HAS_LINK]->(link:Link)
      OPTIONAL MATCH (rec)-[:TAGGED]->(t:Tag)
      WITH rec, link, s.score AS score, COLLECT(DISTINCT t.tag) AS tags
      RETURN rec.movieId AS movieId,
             rec.title AS title,
             rec.genres AS genres,
             link.imdbId AS imdbId,
             link.tmdbId AS tmdbId,
             [tag IN tags WHERE tag IS NOT NULL AND tag <> ''] AS nonEmptyTags
      ORDER BY score DESC
      LIMIT $limit
    """
      .query(ResultMapper.fromFunction(Movie.apply _))
      .stream(driver)

  def getMovieDetailsByExternalId(externalId: String): Task[Option[Movie]] =
    c"""
      MATCH (m:Movie)-[:HAS_LINK]->(link:Link)
      WHERE link.imdbId = $externalId OR link.tmdbId = $externalId
      OPTIONAL MATCH (m)-[:TAGGED]->(t:Tag)
      WITH m, link, COLLECT(COALESCE(t.tag, '')) AS tags
      RETURN m.movieId AS movieId, m.title AS title, m.genres AS genres,
             link.imdbId AS imdbId, link.tmdbId AS tmdbId, [tag IN tags WHERE tag <> ''] AS nonEmptyTags
    """
      .query(ResultMapper.fromFunction(Movie.apply _))
      .stream(driver)
      .runHead

  def getMovieById(movieId: Int): Task[Option[Movie]] =
    c"""
    MATCH (m:Movie {movieId: $movieId})
      OPTIONAL MATCH (m)-[:HAS_LINK]->(link:Link)
      OPTIONAL MATCH (m)-[:TAGGED]->(t:Tag)
      WITH m, link, COLLECT(t.tag) AS tags
      RETURN m.movieId AS movieId, m.title AS title, m.genres AS genres,
             link.imdbId AS imdbId, link.tmdbId AS tmdbId, tags
  """
      .query(ResultMapper.fromFunction(Movie.apply _))
      .stream(driver)
      .runHead

  def getRandomMovies(limit: Int): ZStream[Any, Throwable, Movie] =
    c"""
      MATCH (m:Movie)
      OPTIONAL MATCH (m)-[:HAS_LINK]->(link:Link)
      OPTIONAL MATCH (m)-[:TAGGED]->(t:Tag)
      WITH m, link, COLLECT(t.tag) AS tags
      RETURN m.movieId AS movieId, m.title AS title, m.genres AS genres,
             link.imdbId AS imdbId, link.tmdbId AS tmdbId, tags
      ORDER BY rand()
      LIMIT $limit
    """
      .query(ResultMapper.fromFunction(Movie.apply _))
      .stream(driver)

  def findMoviesByTitle(searchStr: String, limit: Int): ZStream[Any, Throwable, Movie] =
    c"""
    MATCH (m:Movie)
    OPTIONAL MATCH (m)-[:HAS_LINK]->(link:Link)
    WHERE toLower(m.title) CONTAINS toLower($searchStr)
    RETURN m.movieId AS movieId,
           m.title AS title,
           m.genres AS genres,
           link.imdbId AS imdbId,
           link.tmdbId AS tmdbId
    LIMIT $limit
  """
      .query(ResultMapper.fromFunction(Movie.apply _))
      .stream(driver)

}

object Neo4jServiceImpl {
  val layer = ZLayer.fromFunction(Neo4jServiceImpl.apply _)
}
