package com.yourname.rareanimes

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document

class RareAnimesProvider : MainAPI() {
    override var mainUrl = "https://www.rareanimes.mov"
    override var name = "Rare Animes"
    override val lang = "hi"
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Anime, TvType.Movie)
    override val hasMainPage = true
    override val hasQuickSearch = false

    // Helper: clean text
    private fun String.clean(): String = this.trim().replace(Regex("\\s+"), " ")

    // Helper: get poster image
    private fun Document.getPoster(): String? {
        return this.selectFirst("img.wp-post-image, .post-thumbnail img, .entry-content img[class*='wp-image']")
            ?.attr("src")
    }

    // 1. MAIN PAGE - shows content when you open the provider
    override suspend fun getMainPage(page: Int, request: MainPageRequest?): HomePageResponse {
        val document = app.get(mainUrl).document
        val home = ArrayList<HomePageList>()

        // Parse all post links from the homepage
        val list = ArrayList<SearchResponse>()
        document.select("article, .post, .item, a[href*=/]").forEach { element ->
            val url = element.attr("href")
            if (url.startsWith(mainUrl) && url != mainUrl && 
                !url.contains("/category/") && !url.contains("/tag/") && !url.contains("/author/")) {
                val name = element.text().clean()
                if (name.isNotEmpty()) {
                    val posterUrl = element.selectFirst("img")?.attr("src")
                    val type = if (url.contains("/movie/") || url.contains("/film/") || name.contains("Movie"))
                        TvType.Movie else TvType.TvSeries
                    val response = if (type == TvType.Movie) 
                        newMovieSearchResponse(name, url) { this.posterUrl = posterUrl }
                    else 
                        newTvSeriesSearchResponse(name, url) { this.posterUrl = posterUrl }
                    list.add(response)
                }
            }
        }

        home.add(HomePageList(list, "Latest Episodes", HomePageListType.LatestEpisodes))
        return HomePageResponse(home)
    }

    // 2. SEARCH - handles user searches
    override suspend fun search(query: String): List<SearchResponse>? {
        val document = app.get("$mainUrl/search/${query.replace(" ", "+")}/").document
        val results = ArrayList<SearchResponse>()

        document.select("article, .post, .item, a[href*=/]").forEach { element ->
            val url = element.attr("href")
            if (url.startsWith(mainUrl) && url != mainUrl && 
                !url.contains("/category/") && !url.contains("/tag/") && !url.contains("/author/")) {
                val name = element.text().clean()
                if (name.isNotEmpty()) {
                    val posterUrl = element.selectFirst("img")?.attr("src")
                    val type = if (url.contains("/movie/") || url.contains("/film/") || name.contains("Movie"))
                        TvType.Movie else TvType.TvSeries
                    val response = if (type == TvType.Movie) 
                        newMovieSearchResponse(name, url) { this.posterUrl = posterUrl }
                    else 
                        newTvSeriesSearchResponse(name, url) { this.posterUrl = posterUrl }
                    results.add(response)
                }
            }
        }

        return results
    }

    // 3. LOAD - fetches details for a specific show/movie
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title, h1.post-title, article h1")?.text()?.clean()
            ?: document.title().clean()
        val posterUrl = document.getPoster()
        val plot = document.selectFirst(".entry-content p, .description p, .summary p")?.text()?.clean()
        val tags = document.select(".entry-tags a, .tags a, .genres a").map { it.text().clean() }
        val actors = document.select(".cast a, .actors a").map { it.text().clean() }

        val tvType = if (url.contains("/movie/") || url.contains("/film/") || title.contains("Movie"))
            TvType.Movie else TvType.TvSeries

        if (tvType == TvType.TvSeries) {
            val episodes = ArrayList<Episode>()
            document.select(".episode-item a, .eplister a, .entry-content a[href*=/episode/], .entry-content a[href*=/watch/]")
                .forEach { element ->
                    val episodeUrl = element.attr("href")
                    val episodeName = element.text().clean().ifEmpty { "Episode ${episodes.size + 1}" }
                    episodes.add(Episode(episodeName, episodeUrl))
                }

            if (episodes.isNotEmpty()) {
                return newTvSeriesLoadResponse(title, url, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                    this.plot = plot
                    this.tags = tags
                    this.actors = actors
                    this.episodes = episodes
                }
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie) {
            this.posterUrl = posterUrl
            this.plot = plot
            this.tags = tags
            this.actors = actors
        }
    }

    // 4. LOAD LINKS - extracts the actual video URL (MOST IMPORTANT)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            // Step 1: Get the episode page
            val episodeDocument = app.get(data).document

            // Step 2: Find the "Watch/Download" link (redirector like codedew.com)
            var watchLink = episodeDocument.selectFirst("a[href*='codedew.com'], a[href*='watch'], a[href*='download'], .play-button a, .watch-link a")
                ?.attr("href")

            if (watchLink == null) {
                watchLink = episodeDocument.select("a[href]")
                    .find { it.text().contains("watch", ignoreCase = true) || it.text().contains("download", ignoreCase = true) }
                    ?.attr("href")
            }

            if (watchLink == null) return false

            // Step 3: Follow the redirector (allowRedirects = false to intercept the redirect)
            val redirectResponse = app.get(watchLink, allowRedirects = false)
            val videoHostUrl = redirectResponse.headers["Location"]

            if (videoHostUrl == null) {
                return extractVideoFromPage(redirectResponse.document, callback, subtitleCallback)
            }

            // Step 4: Fetch the video host page and extract the video
            val videoHostDocument = app.get(videoHostUrl).document
            return extractVideoFromPage(videoHostDocument, callback, subtitleCallback)

        } catch (e: Exception) {
            return false
        }
    }

    // Helper: extract video from any page
    private suspend fun extractVideoFromPage(
        document: Document,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ): Boolean {
        // Try direct video source
        val videoSource = document.selectFirst("video source[src]")
        if (videoSource != null) {
            val videoUrl = videoSource.attr("src")
            callback.invoke(ExtractorLink(name, name, videoUrl, mainUrl, Qualities.Unknown.value, videoUrl.contains(".m3u8")))
            return true
        }

        // Try iframe
        val iframe = document.selectFirst("iframe[src]")
        if (iframe != null) {
            val iframeDocument = app.get(iframe.attr("src")).document
            return extractVideoFromPage(iframeDocument, callback, subtitleCallback)
        }

        // Try direct video link
        val videoLink = document.select("a[href]").find { it.attr("href").contains(".mp4") || it.attr("href").contains(".m3u8") }
        if (videoLink != null) {
            callback.invoke(ExtractorLink(name, name, videoLink.attr("href"), mainUrl, Qualities.Unknown.value, videoLink.attr("href").contains(".m3u8")))
            return true
        }

        // Try searching in JavaScript
        val scriptContent = document.select("script").joinToString(" ") { it.html() }
        val regex = Regex("""(https?://[^\s"']+\.(?:mp4|m3u8)[^\s"']*)""")
        val match = regex.find(scriptContent)
        if (match != null) {
            callback.invoke(ExtractorLink(name, name, match.groupValues[1], mainUrl, Qualities.Unknown.value, match.groupValues[1].contains(".m3u8")))
            return true
        }

        return false
    }
}