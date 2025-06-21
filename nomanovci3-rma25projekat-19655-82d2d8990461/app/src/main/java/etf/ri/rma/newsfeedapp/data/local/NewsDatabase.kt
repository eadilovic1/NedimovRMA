package etf.ri.rma.newsfeedapp.data.local

import android.content.Context
import androidx.room.*
import etf.ri.rma.newsfeedapp.data.local.dao.*
import etf.ri.rma.newsfeedapp.data.local.entity.*
import etf.ri.rma.newsfeedapp.data.local.relation.NewsWithTags
import etf.ri.rma.newsfeedapp.model.NewsItem

@Database(
    entities = [NewsEntity::class, TagEntity::class, NewsTagCrossRef::class],
    version = 1,
    exportSchema = false
)
abstract class NewsDatabase : RoomDatabase() {
    abstract fun newsDao(): NewsDao
    abstract fun tagDao(): TagDao

    companion object {
        private const val DB_NAME = "news-db"
        @Volatile private var INSTANCE: NewsDatabase? = null

        fun getInstance(context: Context): NewsDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, NewsDatabase::class.java, DB_NAME)
                    .build().also { INSTANCE = it }
            }
    }

    // --------------------------
    // Metode iz zadatka:
    // --------------------------

    suspend fun saveNews(item: NewsItem): Boolean {
        if (newsDao().loadAll().any { it.uuid == item.uuid }) return false
        newsDao().insert(
            NewsEntity(
                uuid          = item.uuid,
                title         = item.title,
                snippet       = item.snippet,
                source        = item.source,
                publishedDate = item.publishedDate,
                imageUrl      = item.imageUrl,
                category      = item.category,
                isFeatured    = item.isFeatured
            )
        )
        return true
    }

    suspend fun allNews(): List<NewsItem> =
        newsDao().loadAll().map { e ->
            toNewsItem(e, getTags(e.id))
        }

    suspend fun getNewsWithCategory(category: String): List<NewsItem> =
        newsDao().loadByCategory(category).map { e ->
            toNewsItem(e, getTags(e.id))
        }

    suspend fun addTags(tags: List<String>, newsId: Int): Int {
        var newCount = 0
        tags.forEach { value ->
            val existing = tagDao().findByValue(value)
            val tagId = existing?.id ?: run {
                val id = tagDao().insert(TagEntity(value = value)).toInt()
                if (id != -1) newCount++
                id
            }
            tagDao().insertCrossRef(NewsTagCrossRef(newsId, tagId))
        }
        return newCount
    }

    suspend fun getTags(newsId: Int): List<String> =
        newsDao().loadWithTags(newsId).tags.map { it.value }

    suspend fun getSimilarNews(tags: List<String>): List<NewsItem> {
        val firstTwo = tags.take(2)
        return newsDao().loadSimilar(firstTwo).map { rel ->
            toNewsItem(rel.news, rel.tags.map { it.value })
        }
    }

    // mapiranje entiteta u model
    private fun toNewsItem(entity: NewsEntity, tags: List<String>) = NewsItem(
        uuid          = entity.uuid,
        title         = entity.title,
        snippet       = entity.snippet,
        source        = entity.source,
        publishedDate = entity.publishedDate,
        imageUrl      = entity.imageUrl,
        category      = entity.category,
        isFeatured    = entity.isFeatured,
        imageTags     = ArrayList(tags)
    )
}
