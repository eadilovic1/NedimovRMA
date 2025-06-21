package etf.ri.rma.newsfeedapp.model

data class NewsItem(
    val uuid: String,
    val title: String,
    val snippet: String,
    val source: String,
    val publishedDate: String,
    val imageUrl: String,
    val category: String,
    var isFeatured: Boolean,
    var imageTags: ArrayList<String> = arrayListOf()
)
