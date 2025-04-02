1. Clone repo
2. cd into the repo
3. Run docker-compose up
4. Download and unzip dataset https://grouplens.org/datasets/movielens/latest/ (ml-latest-small)
5. Copy dataset files into docker container:

docker cp movies.csv neo4j-movies:/var/lib/neo4j/import/
docker cp ratings.csv neo4j-movies:/var/lib/neo4j/import/
docker cp tags.csv neo4j-movies:/var/lib/neo4j/import/
docker cp links.csv neo4j-movies:/var/lib/neo4j/import/

6. docker exec -it neo4j-movies /bin/bash
7. Run ./bin/cypher-shell (for first time it will ask to change the password) (set the same as app is using)
8. Import data

CALL apoc.periodic.iterate(
  "LOAD CSV WITH HEADERS FROM 'file:///movies.csv' AS row RETURN row",
  "MERGE (m:Movie {movieId: toInteger(row.movieId)})
   SET m.title = row.title, m.genres = split(row.genres, '|')",
  {batchSize: 1000, parallel: true}
);

CALL apoc.periodic.iterate(
  "LOAD CSV WITH HEADERS FROM 'file:///ratings.csv' AS row RETURN row",
  "MERGE (u:User {userId: toInteger(row.userId)})
   MERGE (m:Movie {movieId: toInteger(row.movieId)})
   MERGE (u)-[:RATED {score: toFloat(row.rating)}]->(m)",
  {batchSize: 1000, parallel: true}
);

CALL apoc.periodic.iterate(
  "LOAD CSV WITH HEADERS FROM 'file:///tags.csv' AS row RETURN row",
  "MERGE (u:User {userId: toInteger(row.userId)})
   MERGE (m:Movie {movieId: toInteger(row.movieId)})
   MERGE (t:Tag {tag: row.tag})
   MERGE (m)-[:TAGGED]->(t)",
  {batchSize: 1000, parallel: true}
);

CALL apoc.periodic.iterate(
  "LOAD CSV WITH HEADERS FROM 'file:///links.csv' AS row RETURN row",
  "MERGE (m:Movie {movieId: toInteger(row.movieId)})
   MERGE (l:Link {imdbId: row.imdbId, tmdbId: row.tmdbId})
   MERGE (m)-[:HAS_LINK]->(l)",
  {batchSize: 1000, parallel: true}
);
   
9. Collaborative filtering to create SIMILAR relationships

CALL apoc.periodic.iterate(
  "
  MATCH (m1:Movie)<-[:RATED]-(u:User)-[:RATED]->(m2:Movie)
  WHERE m1 <> m2 AND id(m1) < id(m2)
  WITH m1, m2, COUNT(DISTINCT u) AS commonRaters
  WHERE commonRaters >= 1
  RETURN m1, m2
  ",
  "
  WITH m1, m2,
       apoc.coll.intersection(m1.genres, m2.genres) AS sharedGenres
  WITH m1, m2, sharedGenres,
       size(sharedGenres) AS genreScore

  OPTIONAL MATCH (m1)-[:TAGGED]->(t1:Tag)
  WITH m1, m2, genreScore, COLLECT(DISTINCT t1.tag) AS tags1
  OPTIONAL MATCH (m2)-[:TAGGED]->(t2:Tag)
  WITH m1, m2, genreScore, tags1, COLLECT(DISTINCT t2.tag) AS tags2
  WITH m1, m2, genreScore,
       size(apoc.coll.intersection(tags1, tags2)) AS tagScore

  OPTIONAL MATCH (m1)<-[r1:RATED]-()
  WITH m1, m2, genreScore, tagScore, avg(r1.rating) AS r1avg
  OPTIONAL MATCH (m2)<-[r2:RATED]-()
  WITH m1, m2, genreScore, tagScore, r1avg, avg(r2.rating) AS r2avg

  WITH m1, m2, genreScore, tagScore,
       CASE
         WHEN r1avg IS NOT NULL AND r2avg IS NOT NULL THEN
           (genreScore * 1.5 + tagScore * 2.0 - abs(r1avg - r2avg) * 1.0)
         ELSE
           (genreScore * 1.5 + tagScore * 2.0)
       END AS score

  WHERE score > 0

  MERGE (m1)-[:SIMILAR {score: score}]->(m2)
  ",
  {batchSize: 100, parallel: true}
);

