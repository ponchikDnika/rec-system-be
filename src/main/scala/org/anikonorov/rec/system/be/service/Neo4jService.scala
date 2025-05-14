package org.anikonorov.rec.system.be.service

import neotypes.StreamDriver
import neotypes.mappers.ResultMapper
import neotypes.syntax.all._
import neotypes.zio.stream.ZioStream
import org.anikonorov.rec.system.be.models.{Movie, Pagination}
import zio.stream.ZStream
import zio.{Task, ZLayer}

trait Neo4jService {
  def getSimilarMovies(movieId: Long, pagination: Pagination): ZStream[Any, Throwable, Movie]
  def getRecommendedMoviesByTag(tag: String, pagination: Pagination): ZStream[Any, Throwable, Movie]
  def getRecommendedMoviesByGenre(genre: String, pagination: Pagination): ZStream[Any, Throwable, Movie]
  def getRandomMovies(pagination: Pagination): ZStream[Any, Throwable, Movie]
  def getMoviesByTitle(searchStr: String, pagination: Pagination): ZStream[Any, Throwable, Movie]
  def getMovieDetailsByExternalId(externalId: String): Task[Option[Movie]]
  def getMovieById(movieId: Int): Task[Option[Movie]]
}

case class Neo4jServiceImpl(driver: StreamDriver[ZioStream, Task]) extends Neo4jService {

  def getRecommendedMoviesByTag(tag: String, pagination: Pagination): ZStream[Any, Throwable, Movie] =
    c"""
    MATCH (m:Movie)-[:TAGGED]->(t:Tag)
    WHERE toLower(t.tag) = toLower($tag)
    OPTIONAL MATCH (rec)-[:HAS_LINK]->(link:Link)
    OPTIONAL MATCH (rec)-[:TAGGED]->(t:Tag)
    WITH rec, link, COLLECT(DISTINCT t.tag) AS tags
      RETURN rec.movieId AS movieId,
             rec.title AS title,
             rec.genres AS genres,
             link.imdbId AS imdbId,
             link.tmdbId AS tmdbId,
             [tag IN tags WHERE tag IS NOT NULL AND tag <> ''] AS nonEmptyTags
  """
      .query(ResultMapper.fromFunction(Movie.apply _))
      .stream(driver)
      .drop((pagination.page - 1) * pagination.limit)
      .take(pagination.limit)

  override def getRecommendedMoviesByGenre(genre: String, pagination: Pagination): ZStream[Any, Throwable, Movie] =
    c"""
      MATCH (m:Movie)
      WHERE ANY(genre IN m.genres WHERE toLower(genre) = toLower($genre))
      OPTIONAL MATCH (m)-[:HAS_LINK]->(link:Link)
      OPTIONAL MATCH (m)-[:TAGGED]->(t:Tag)
      WITH m, link, COLLECT(DISTINCT t.tag) AS tags
      RETURN m.movieId AS movieId,
             m.title AS title,
             m.genres AS genres,
             link.imdbId AS imdbId,
             link.tmdbId AS tmdbId,
             [tag IN tags WHERE tag IS NOT NULL AND tag <> ''] AS nonEmptyTags
    """
      .query(ResultMapper.fromFunction(Movie.apply _))
      .stream(driver)
      .drop((pagination.page - 1) * pagination.limit)
      .take(pagination.limit)

  def getSimilarMovies(movieId: Long, pagination: Pagination): ZStream[Any, Throwable, Movie] =
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
    """
      .query(ResultMapper.fromFunction(Movie.apply _))
      .stream(driver)
      .drop((pagination.page - 1) * pagination.limit)
      .take(pagination.limit)

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

  def getRandomMovies(pagination: Pagination): ZStream[Any, Throwable, Movie] =
    c"""
      MATCH (m:Movie)
      OPTIONAL MATCH (m)-[:HAS_LINK]->(link:Link)
      OPTIONAL MATCH (m)-[:TAGGED]->(t:Tag)
      WITH m, link, COLLECT(t.tag) AS tags
      RETURN m.movieId AS movieId, m.title AS title, m.genres AS genres,
             link.imdbId AS imdbId, link.tmdbId AS tmdbId, tags
      ORDER BY rand()
    """
      .query(ResultMapper.fromFunction(Movie.apply _))
      .stream(driver)
      .drop((pagination.page - 1) * pagination.limit)
      .take(pagination.limit)

  override def getMoviesByTitle(searchStr: String, pagination: Pagination): ZStream[Any, Throwable, Movie] =
    c"""
      MATCH (m:Movie)
      WHERE toLower(m.title) CONTAINS toLower($searchStr)
      OPTIONAL MATCH (m)-[:HAS_LINK]->(link:Link)
      OPTIONAL MATCH (m)-[:TAGGED]->(t:Tag)
      WITH m, link, COLLECT(DISTINCT t.tag) AS tags
      RETURN m.movieId AS movieId,
             m.title AS title,
             m.genres AS genres,
             link.imdbId AS imdbId,
             link.tmdbId AS tmdbId,
             [tag IN tags WHERE tag IS NOT NULL AND tag <> ''] AS nonEmptyTags
    """
      .query(ResultMapper.fromFunction(Movie.apply _))
      .stream(driver)
      .drop((pagination.page - 1) * pagination.limit)
      .take(pagination.limit)

}

object Neo4jServiceImpl {
  val layer = ZLayer.fromFunction(Neo4jServiceImpl.apply _)
}
