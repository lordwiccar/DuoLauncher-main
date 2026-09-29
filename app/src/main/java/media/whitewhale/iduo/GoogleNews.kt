package media.whitewhale.iduo

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** A Google News edition: its country ([gl]), language, and the `hl` value Google expects. */
enum class NewsEdition(val gl: String, val language: String, val hl: String, val label: String) {
    CZ("CZ", "cs", "cs", "Česko"),
    SK("SK", "sk", "sk", "Slovensko"),
    PL("PL", "pl", "pl", "Polska"),
    DE("DE", "de", "de", "Deutschland"),
    AT("AT", "de", "de", "Österreich"),
    CH("CH", "de", "de", "Schweiz"),
    US("US", "en", "en-US", "United States"),
    GB("GB", "en", "en-GB", "United Kingdom");

    val ceid get() = "$gl:$language"
}

/** Google News sections; [TOP] is the edition's front page, the rest are its topic sections. */
enum class NewsTopic(@StringRes val label: Int, val section: String?) {
    TOP(R.string.news_topic_top, null),
    WORLD(R.string.news_topic_world, "WORLD"),
    NATION(R.string.news_topic_nation, "NATION"),
    BUSINESS(R.string.news_topic_business, "BUSINESS"),
    TECHNOLOGY(R.string.news_topic_technology, "TECHNOLOGY"),
    SCIENCE(R.string.news_topic_science, "SCIENCE"),
    HEALTH(R.string.news_topic_health, "HEALTH"),
    SPORTS(R.string.news_topic_sports, "SPORTS"),
    ENTERTAINMENT(R.string.news_topic_entertainment, "ENTERTAINMENT"),
}

/** Google's public RSS address for one section of one edition. */
fun googleNewsFeedUrl(edition: NewsEdition, topic: NewsTopic): String {
    val query = "hl=${edition.hl}&gl=${edition.gl}&ceid=${edition.ceid}"
    return if (topic.section == null) "https://news.google.com/rss?$query"
    else "https://news.google.com/rss/headlines/section/topic/${topic.section}?$query"
}

/** The edition for a language and country: an exact match, else the language's first edition, else the US. */
fun defaultNewsEdition(language: String, country: String): NewsEdition =
    NewsEdition.entries.firstOrNull { it.language == language && it.gl.equals(country, ignoreCase = true) }
        ?: NewsEdition.entries.firstOrNull { it.language == language }
        ?: NewsEdition.US

/** Sources for the chosen sections, in [NewsTopic] order so the list is stable. */
fun googleNewsSources(edition: NewsEdition, topics: Set<NewsTopic>): List<RssSource> =
    NewsTopic.entries.filter { it in topics }.map { RssSource(googleNewsFeedUrl(edition, it), "Google News") }

private const val PREFS = "google_news"
private const val EDITION = "edition"
private const val TOPICS = "topics"

/** The chosen Google News edition and sections, kept on the device. */
internal object GoogleNewsSettings {
    var edition by mutableStateOf(NewsEdition.US)
        private set
    var topics by mutableStateOf(setOf(NewsTopic.TOP))
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val language = AppLanguage.current(context).ifEmpty { AppLanguage.systemLocale(context).language }
        edition = NewsEdition.entries.firstOrNull { it.name == prefs.getString(EDITION, null) }
            ?: defaultNewsEdition(language, Locale.getDefault().country)
        topics = prefs.getString(TOPICS, null)?.split(',')?.mapNotNull { name -> NewsTopic.entries.firstOrNull { it.name == name } }
            ?.toSet()?.takeIf { it.isNotEmpty() } ?: setOf(NewsTopic.TOP)
        NewsFeeds.googleNews.replaceSources(context, googleNewsSources(edition, topics))
    }

    fun setEdition(context: Context, value: NewsEdition) { edition = value; save(context) }

    /** Turns a section on or off; the last section stays on so the page is never empty. */
    fun toggle(context: Context, topic: NewsTopic) {
        val next = if (topic in topics) topics - topic else topics + topic
        if (next.isEmpty()) return
        topics = next; save(context)
    }

    private fun save(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(EDITION, edition.name).putString(TOPICS, topics.joinToString(",") { it.name }).apply()
        NewsFeeds.googleNews.replaceSources(context, googleNewsSources(edition, topics))
    }
}
