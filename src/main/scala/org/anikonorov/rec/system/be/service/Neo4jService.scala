package org.anikonorov.rec.system.be.service

import neotypes.StreamDriver
import neotypes.mappers.ResultMapper
import neotypes.syntax.all._
import neotypes.zio.stream.ZioStream
import org.anikonorov.rec.system.be.models.{Movie, MovieWithTags}
import zio.stream.ZStream
import zio.{Task, ZLayer}

trait Neo4jService {
  def getRecommendedMoviesForUser(userId: Long, limit: Int = 10): ZStream[Any, Throwable, Movie]
  def getRecommendedMoviesByTag(tag: String, limit: Int = 10): ZStream[Any, Throwable, Movie]
  def getSimilarMovies(movieId: Long, limit: Int = 10): ZStream[Any, Throwable, Movie]
  def getMovieDetails(externalId: String): Task[Option[Movie]]
  def getRandomMovies(limit: Int = 25): ZStream[Any, Throwable, MovieWithTags]
}

case class Neo4jServiceImpl(driver: StreamDriver[ZioStream, Task]) extends Neo4jService {

  def getRecommendedMoviesForUser(userId: Long, limit: Int = 10): ZStream[Any, Throwable, Movie] =
    c"""
      MATCH (u:User {userId: $userId})-[:RATED]->(m:Movie)<-[:RATED]-(other:User)-[:RATED]->(rec:Movie)
      MATCH (rec)-[:HAS_LINK]->(link:Link)
      WHERE NOT (u)-[:RATED]->(rec)
      RETURN rec.movieId AS movieId, rec.title AS title, rec.genres AS genres,
             link.imdbId AS imdbId, link.tmdbId AS tmdbId
      LIMIT $limit
    """
      .query(ResultMapper.fromFunction(Movie.apply _))
      .stream(driver)

  def getRecommendedMoviesByTag(tag: String, limit: Int = 10): ZStream[Any, Throwable, Movie] =
    c"""
      MATCH (m:Movie)-[:TAGGED]->(t:Tag {tag: $tag})
      MATCH (m)-[:HAS_LINK]->(link:Link)
      RETURN m.movieId AS movieId, m.title AS title, m.genres AS genres,
             link.imdbId AS imdbId, link.tmdbId AS tmdbId
      LIMIT $limit
    """
      .query(ResultMapper.fromFunction(Movie.apply _))
      .stream(driver)

  def getSimilarMovies(movieId: Long, limit: Int = 10): ZStream[Any, Throwable, Movie] =
    c"""
      MATCH (m:Movie {movieId: $movieId})-[:SIMILAR]->(rec:Movie)
      MATCH (rec)-[:HAS_LINK]->(link:Link)
      RETURN rec.movieId AS movieId, rec.title AS title, rec.genres AS genres,
             link.imdbId AS imdbId, link.tmdbId AS tmdbId
      LIMIT $limit
    """
      .query(ResultMapper.fromFunction(Movie.apply _))
      .stream(driver)

  def getMovieDetails(externalId: String): Task[Option[Movie]] =
    c"""
      MATCH (m:Movie)-[:HAS_LINK]->(link:Link)
      WHERE link.imdbId = $externalId OR link.tmdbId = $externalId
      RETURN m.movieId AS movieId, m.title AS title, m.genres AS genres,
             link.imdbId AS imdbId, link.tmdbId AS tmdbId
    """
      .query(ResultMapper.fromFunction(Movie.apply _))
      .stream(driver)
      .runHead

  def getRandomMovies(limit: Int = 25): ZStream[Any, Throwable, MovieWithTags] =
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
      .query(ResultMapper.fromFunction { (movieId: Long, title: String, genres: List[String], imdbId: Option[String], tmdbId: Option[String], tags: List[String]) =>
        MovieWithTags(Movie(movieId, title, genres, imdbId, tmdbId),  tags)
      })
      .stream(driver)
}

object Neo4jServiceImpl {
  val layer = ZLayer.fromFunction(Neo4jServiceImpl.apply _)
}
