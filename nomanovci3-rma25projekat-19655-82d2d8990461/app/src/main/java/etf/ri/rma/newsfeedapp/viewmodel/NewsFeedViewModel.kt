package etf.ri.rma.newsfeedapp.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import etf.ri.rma.newsfeedapp.data.local.NewsDatabase
import etf.ri.rma.newsfeedapp.data.network.ImaggaDAO
import etf.ri.rma.newsfeedapp.data.network.NewsDAO
import etf.ri.rma.newsfeedapp.model.NewsItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.time.LocalDate

private const val TAG = "NewsFeedVM"

data class DateRange(val start: LocalDate, val end: LocalDate)

class NewsFeedViewModel(app: Application) : AndroidViewModel(app) {
    private val db        = NewsDatabase.getInstance(app)
    private val newsDAO   = NewsDAO
    private val imaggaDAO = ImaggaDAO

    var selectedCategory: String by mutableStateOf("general"); private set
    var selectedDateRange: DateRange? by mutableStateOf(null); private set
    var unwantedWords: List<String> by mutableStateOf(emptyList()); private set

    private val _allNewsFlow = MutableStateFlow<List<NewsItem>>(emptyList())
    val allNewsFlow: StateFlow<List<NewsItem>> = _allNewsFlow

    private val similarCache = mutableMapOf<String, List<NewsItem>>()

    init {
        // Prvi load iz baze, bez duplikata
        viewModelScope.launch(Dispatchers.IO) {
            _allNewsFlow.value = db.allNews().distinctBy { it.uuid }
        }
    }

    fun updateFilters(category: String, dateRange: DateRange?, words: List<String>) {
        selectedCategory  = category
        selectedDateRange = dateRange
        unwantedWords     = words
    }

    fun onCategorySelected(newCategory: String) {
        selectedCategory = newCategory
        viewModelScope.launch(Dispatchers.IO) {
            val rawList = try {
                if (newCategory == "general") {
                    val fetched = newsDAO.getAllStories()
                    fetched.forEach { db.saveNews(it) }
                    fetched
                } else {
                    val fetched = try {
                        newsDAO.getTopStoriesByCategory(newCategory).also { list ->
                            list.forEach { db.saveNews(it) }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Cannot fetch $newCategory from network, using local", e)
                        emptyList()
                    }
                    if (fetched.isNotEmpty()) {
                        db.getNewsWithCategory(newCategory) + fetched
                    } else {
                        db.getNewsWithCategory(newCategory)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onCategorySelected", e)
                db.getNewsWithCategory(newCategory)
            }

            val combined = rawList
                .distinctBy { it.uuid }
                .toMutableList()
                .apply {
                    shuffle()
                    forEach { it.isFeatured = false }
                    take(3).forEach { it.isFeatured = true }
                }

            _allNewsFlow.value = combined
        }
    }

    /**
     * Kad otvoriš detalje:
     *  1) fetch image-tags i addTags
     *  2) saveNews
     *  3) pokušaj network getSimilarStories, inače offline fallback na DB.getNewsWithCategory
     */
    fun loadDetailsData(newsItem: NewsItem, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1) image tags
                if (newsItem.imageTags.isEmpty()) {
                    try {
                        val tags = imaggaDAO.getTags(newsItem.imageUrl)
                        newsItem.imageTags.addAll(tags)
                        val id = db.newsDao()
                            .loadAll()
                            .first { it.uuid == newsItem.uuid }
                            .id
                        db.addTags(tags, id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Cannot fetch/save image tags", e)
                    }
                }

                // 2) pohrani vijest
                db.saveNews(newsItem)

                // 3) slične priče
                val similar = try {
                    newsDAO.getSimilarStories(newsItem.uuid).also { list ->
                        list.forEach { db.saveNews(it) }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Falling back to local similar by category", e)
                    db.getNewsWithCategory(newsItem.category)
                        .filter { it.uuid != newsItem.uuid }
                        .sortedByDescending { it.publishedDate }
                        .take(2)
                }

                similarCache[newsItem.uuid] = similar
            } finally {
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }

    fun getAllStories(): List<NewsItem> = newsDAO.getAllStories()
    fun getSimilarFromCache(uuid: String): List<NewsItem> =
        similarCache[uuid] ?: emptyList()
}
