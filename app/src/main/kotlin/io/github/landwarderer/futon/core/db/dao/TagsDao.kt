package io.github.landwarderer.futon.core.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.landwarderer.futon.core.db.entity.TagEntity

@Dao
abstract class TagsDao {

	@Query("SELECT * FROM tags WHERE source = :source")
	abstract suspend fun findTags(source: String): List<TagEntity>

	@Query(
		"""SELECT tags.* FROM tags
		LEFT JOIN manga_tags ON tags.tag_id = manga_tags.tag_id
		WHERE manga_tags.manga_id IN (SELECT manga_id FROM history UNION SELECT manga_id FROM favourites)
		GROUP BY tags.title 
		ORDER BY COUNT(manga_id) DESC 
		LIMIT :limit""",
	)
	abstract suspend fun findPopularTags(limit: Int): List<TagEntity>

	@Query(
		"""SELECT tags.* FROM tags
		LEFT JOIN manga_tags ON tags.tag_id = manga_tags.tag_id 
		WHERE tags.source = :source  
		GROUP BY tags.title
		ORDER BY COUNT(manga_id) DESC 
		LIMIT :limit""",
	)
	abstract suspend fun findPopularTags(source: String, limit: Int): List<TagEntity>

	@Query(
		"""SELECT tags.* FROM tags
		LEFT JOIN manga_tags ON tags.tag_id = manga_tags.tag_id 
		WHERE tags.source = :source  
		GROUP BY tags.title
		ORDER BY COUNT(manga_id) ASC 
		LIMIT :limit""",
	)
	abstract suspend fun findRareTags(source: String, limit: Int): List<TagEntity>

	@Query(
		"""SELECT tags.* FROM tags
		LEFT JOIN manga_tags ON tags.tag_id = manga_tags.tag_id 
		WHERE tags.source = :source AND title LIKE :query 
		GROUP BY tags.title
		ORDER BY COUNT(manga_id) DESC 
		LIMIT :limit""",
	)
	abstract suspend fun findTags(source: String, query: String, limit: Int): List<TagEntity>

	@Query(
		"""SELECT tags.* FROM tags
		LEFT JOIN manga_tags ON tags.tag_id = manga_tags.tag_id 
		WHERE title LIKE :query AND manga_tags.manga_id IN (SELECT manga_id FROM history UNION SELECT manga_id FROM favourites)
		GROUP BY tags.title
		ORDER BY COUNT(manga_id) DESC 
		LIMIT :limit""",
	)
	abstract suspend fun findTags(query: String, limit: Int): List<TagEntity>

	@Query("SELECT * FROM tags WHERE title LIKE :query GROUP BY title LIMIT :limit")
	abstract suspend fun searchAllTags(query: String, limit: Int): List<TagEntity>

	@Query(
		"""
		SELECT tags.* FROM manga_tags 
		LEFT JOIN tags ON tags.tag_id = manga_tags.tag_id 
		WHERE manga_tags.manga_id IN (SELECT manga_id FROM manga_tags WHERE tag_id = :tagId)
		GROUP BY tags.tag_id 
		ORDER BY COUNT(manga_id) DESC;
	""",
	)
	abstract suspend fun findRelatedTags(tagId: Long): List<TagEntity>

	@Query(
		"""
		SELECT tags.* FROM manga_tags 
		LEFT JOIN tags ON tags.tag_id = manga_tags.tag_id 
		WHERE manga_tags.manga_id IN (SELECT manga_id FROM manga_tags WHERE tag_id IN (:ids))
		GROUP BY tags.tag_id 
		ORDER BY COUNT(manga_id) DESC;
	""",
	)
	abstract suspend fun findRelatedTags(ids: Set<Long>): List<TagEntity>

	/**
	 * How many stored manga carry each tag, by title across every source: how common a tag is in
	 * everything the app has seen, which is what tells `action` from `villainess`.
	 */
	@Query(
		"""SELECT LOWER(TRIM(tags.title)) AS title, COUNT(DISTINCT manga_tags.manga_id) AS count FROM tags
		JOIN manga_tags ON tags.tag_id = manga_tags.tag_id
		GROUP BY LOWER(TRIM(tags.title))""",
	)
	abstract suspend fun countMangaPerTag(): List<TagFrequency>

	@Query("SELECT COUNT(DISTINCT manga_id) FROM manga_tags")
	abstract suspend fun countTaggedManga(): Int

	@Upsert
	abstract suspend fun upsert(tags: Iterable<TagEntity>)
}

/** One row of [TagsDao.countMangaPerTag]: a lowercased tag title and how many manga carry it. */
data class TagFrequency(
	val title: String,
	val count: Int,
)
