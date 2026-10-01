package media.whitewhale.iduo

import android.content.pm.ApplicationInfo

/** The library's folders, in the order it shows them. [RECENT] holds recently opened apps. */
enum class AppCategory(val label: Int) {
    RECENT(R.string.category_recent),
    SOCIAL(R.string.category_social),
    PRODUCTIVITY(R.string.category_productivity),
    UTILITIES(R.string.category_utilities),
    ENTERTAINMENT(R.string.category_entertainment),
    MUSIC(R.string.category_music),
    PHOTO(R.string.category_photo),
    GAMES(R.string.category_games),
    NEWS(R.string.category_news),
    SHOPPING(R.string.category_shopping),
    FINANCE(R.string.category_finance),
    HEALTH(R.string.category_health),
    EDUCATION(R.string.category_education),
    TRAVEL(R.string.category_travel),
    GOOGLE(R.string.category_google),
    MAKER(R.string.category_maker),
    SYSTEM(R.string.category_system),
    OTHER(R.string.category_other),
}

/** Package prefixes of phone makers' own apps. */
private val MAKER_PREFIXES = listOf("com.samsung.", "com.sec.", "com.osp.", "com.miui.", "com.xiaomi.", "com.huawei.",
    "com.hihonor.", "com.oneplus.", "com.oppo.", "com.coloros.", "com.heytap.", "com.realme.", "com.motorola.",
    "com.sonymobile.", "com.sony.", "com.asus.", "com.nothing.", "com.lge.", "com.htc.", "com.vivo.", "com.zte.")

/** Words in a package name or label that tell an app's purpose, checked in this order. */
private val KEYWORDS: List<Pair<AppCategory, List<String>>> = listOf(
    AppCategory.FINANCE to listOf("bank", "banka", "wallet", "finance", "invest", "crypto", "revolut", "paypal", "klarna",
        "airbank", "csob", "moneta", "raiffeisen", "george", "trading", "broker", "pojist", "insurance", "generali"),
    AppCategory.SHOPPING to listOf("shop", "store", "alza", "allegro", "amazon", "ebay", "aliexpress", "temu", "vinted",
        "kaufland", "lidl", "tesco", "albert", "rossmann", "ikea", "zalando", "sinsay", "cropp", "action", "kupi", "eshop", "globus"),
    AppCategory.HEALTH to listOf("health", "fitness", "fitbit", "sleep", "meditat", "calm", "headspace", "water", "hydration", "weight",
        "strava", "yoga", "period", "running", "workout", "diet", "zdravi", "wellbeing"),
    AppCategory.EDUCATION to listOf("education", "edu.", "learn", "duolingo", "school", "skola", "translat", "dictionary", "slovnik",
        "course", "quiz", "khan", "study", "babbel"),
    AppCategory.TRAVEL to listOf("travel", "trip", "booking", "airbnb", "flight", "maps", "navi", "waze", "transport",
        "idos", "uber", "bolt", "taxi", "parking", "lpg", "fuel", "drone", "train", "ryanair", "regiojet", "cd.cz"),
    AppCategory.MUSIC to listOf("music", "spotify", "audio", "podcast", "radio", "audiobook", "audioteka", "deezer", "tidal", "soundcloud"),
    AppCategory.ENTERTAINMENT to listOf("tv", "video", "movie", "film", "netflix", "hbo", "disney", "stream", "voyo",
        "ivysilani", "twitch", "skyshowtime"),
    AppCategory.PHOTO to listOf("photo", "camera", "gallery", "snapseed", "lightroom", "picsart", "canva", "sketch", "vlog", "editor"),
    AppCategory.SOCIAL to listOf("chat", "messenger", "message", "whatsapp", "telegram", "signal", "facebook", "instagram",
        "discord", "slack", "teams", "zoom", "mail", "outlook", "linkedin", "twitter", "threads", "tiktok", "snapchat",
        "viber", "pinterest", "reddit", "orca"),
    AppCategory.PRODUCTIVITY to listOf("office", "docs", "sheet", "note", "todo", "task", "calendar", "drive", "dropbox",
        "onedrive", "scan", "pdf", "word", "excel", "notion", "evernote", "keep", "teleprompter"),
    AppCategory.NEWS to listOf("news", "zpravy", "reader", "rss", "feed", "magazine", "seznam"),
    AppCategory.UTILITIES to listOf("files", "filemanager", "manager", "clean", "vpn", "authenticator", "password", "calculator", "clock",
        "weather", "flashlight", "remote", "keyboard", "launcher", "widget", "theme", "terminal", "backup", "battery"),
)

/** The library folder for an app, from who made it, Android's category for it and its name. */
fun appCategory(packageName: String, label: String, androidCategory: Int, preinstalled: Boolean): AppCategory {
    if (packageName.startsWith("com.google.") || packageName == "com.android.chrome" || packageName == "com.android.vending")
        return AppCategory.GOOGLE
    if (MAKER_PREFIXES.any(packageName::startsWith)) return AppCategory.MAKER
    when (androidCategory) {
        ApplicationInfo.CATEGORY_GAME -> return AppCategory.GAMES
        ApplicationInfo.CATEGORY_AUDIO -> return AppCategory.MUSIC
        ApplicationInfo.CATEGORY_VIDEO -> return AppCategory.ENTERTAINMENT
        ApplicationInfo.CATEGORY_IMAGE -> return AppCategory.PHOTO
        ApplicationInfo.CATEGORY_SOCIAL -> return AppCategory.SOCIAL
        ApplicationInfo.CATEGORY_NEWS -> return AppCategory.NEWS
        ApplicationInfo.CATEGORY_MAPS -> return AppCategory.TRAVEL
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppCategory.PRODUCTIVITY
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> return AppCategory.UTILITIES
    }
    val text = (packageName + " " + label).lowercase()
    KEYWORDS.firstOrNull { (_, words) -> words.any(text::contains) }?.let { return it.first }
    return if (preinstalled || packageName.startsWith("com.android.")) AppCategory.SYSTEM else AppCategory.OTHER
}

/** The library's folders: recent apps first, then every category that has apps, each alphabetical. */
fun <T> libraryFolders(apps: List<T>, recent: List<String>, id: (T) -> String, category: (T) -> AppCategory): List<Pair<AppCategory, List<T>>> {
    val byId = apps.associateBy(id)
    val recentApps = recent.mapNotNull(byId::get).take(8)
    val grouped = apps.groupBy(category)
    return listOfNotNull(recentApps.takeIf { it.isNotEmpty() }?.let { AppCategory.RECENT to it }) +
        AppCategory.entries.filter { it != AppCategory.RECENT }.mapNotNull { category -> grouped[category]?.let { category to it } }
}
