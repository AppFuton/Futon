package io.github.landwarderer.futon.mihon.model

import io.github.landwarderer.futon.mihon.parsers.model.Content
import io.github.landwarderer.futon.mihon.parsers.model.ContentChapter
import io.github.landwarderer.futon.mihon.parsers.model.ContentListFilter
import io.github.landwarderer.futon.mihon.parsers.model.ContentListFilterOptions
import io.github.landwarderer.futon.mihon.parsers.model.ContentPage
import io.github.landwarderer.futon.mihon.parsers.model.ContentRating
import io.github.landwarderer.futon.mihon.parsers.model.ContentSource
import io.github.landwarderer.futon.mihon.parsers.model.ContentState
import io.github.landwarderer.futon.mihon.parsers.model.ContentTag
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag


fun Content.toManga(): Manga {
    return Manga(
        id = id,
        title = title,
        altTitles = altTitles,
        url = url,
        publicUrl = Url,
        rating = rating,
        contentRating = when (contentRating) {
            ContentRating.SAFE -> org.koitharu.kotatsu.parsers.model.ContentRating.SAFE
            ContentRating.SUGGESTIVE -> org.koitharu.kotatsu.parsers.model.ContentRating.SUGGESTIVE
            ContentRating.ADULT -> org.koitharu.kotatsu.parsers.model.ContentRating.ADULT
            null -> null
        },
        coverUrl = coverUrl,
        tags = tags.map { it.toMangaTag() }.toSet(),
        state = state?.toMangaState() ?: org.koitharu.kotatsu.parsers.model.MangaState.ONGOING,
        authors = authors,
        largeCoverUrl = largeCoverUrl,
        description = description,
        chapters = chapters?.map { it.toMangaChapter() },
        source = source.toMangaSource()
    )
}

fun Manga.toContent(source: ContentSource): Content {
    return Content(
        id = id,
        title = title,
        altTitles = altTitles,
        url = url,
        Url = publicUrl,
        rating = rating,
        contentRating = when (contentRating) {
            org.koitharu.kotatsu.parsers.model.ContentRating.SAFE -> ContentRating.SAFE
            org.koitharu.kotatsu.parsers.model.ContentRating.SUGGESTIVE -> ContentRating.SUGGESTIVE
            org.koitharu.kotatsu.parsers.model.ContentRating.ADULT -> ContentRating.ADULT
            null -> null
        },
        coverUrl = coverUrl,
        tags = tags.map { it.toContentTag() }.toSet(),
        state = state?.toContentState(),
        authors = authors,
        largeCoverUrl = largeCoverUrl,
        description = description,
        chapters = chapters?.map { it.toContentChapter(source) },
        source = source
    )
}

fun ContentChapter.toMangaChapter(): MangaChapter {
    return MangaChapter(
        id = id,
        title = title ?: "",
        number = number,
        volume = volume,
        url = url,
        scanlator = scanlator,
        uploadDate = uploadDate,
        branch = branch,
        source = source.toMangaSource()
    )
}

fun MangaChapter.toContentChapter(source: ContentSource): ContentChapter {
    return ContentChapter(
        id = id,
        title = title,
        number = number,
        volume = volume,
        url = url,
        scanlator = scanlator,
        uploadDate = uploadDate,
        branch = branch,
        source = source
    )
}

fun ContentPage.toMangaPage(): MangaPage {
    return MangaPage(
        id = id,
        url = url,
        preview = preview,
        source = source.toMangaSource()
    )
}

fun MangaPage.toContentPage(source: ContentSource): ContentPage {
    return ContentPage(
        id = id,
        url = url,
        preview = preview,
        headers = emptyMap(),
        source = source
    )
}

fun ContentListFilterOptions.toMangaListFilterOptions(): MangaListFilterOptions {
    return MangaListFilterOptions(
        availableTags = availableTags.map { it.toMangaTag() }.toSet(),
        availableStates = availableStates.mapNotNull { it.toMangaState() }.toSet()
    )
}

fun MangaListFilter.toContentListFilter(): ContentListFilter {
    return ContentListFilter(
        query = query,
        tags = tags.map { it.toContentTag() }.toSet(),
        tagsExclude = tagsExclude.map { it.toContentTag() }.toSet()
    )
}

fun ContentState.toMangaState(): MangaState {
    return when (this) {
        ContentState.ONGOING -> MangaState.ONGOING
        ContentState.FINISHED -> MangaState.FINISHED
        ContentState.ABANDONED -> MangaState.ABANDONED
        ContentState.PAUSED -> MangaState.PAUSED
        ContentState.UPCOMING -> MangaState.UPCOMING
        ContentState.RESTRICTED -> MangaState.RESTRICTED
    }
}

fun MangaState.toContentState(): ContentState {
    return when (this) {
        MangaState.ONGOING -> ContentState.ONGOING
        MangaState.FINISHED -> ContentState.FINISHED
        MangaState.ABANDONED -> ContentState.ABANDONED
        MangaState.PAUSED -> ContentState.PAUSED
        MangaState.UPCOMING -> ContentState.UPCOMING
        MangaState.RESTRICTED -> ContentState.RESTRICTED
    }
}

fun ContentTag.toMangaTag(): MangaTag {
    return MangaTag(
        title = title,
        key = key,
        source = source.toMangaSource()
    )
}

fun MangaTag.toContentTag(): ContentTag {
    return ContentTag(
        title = title,
        key = key,
        source = object : ContentSource {
            override val name = "Dummy"
            override val locale = ""
            override val contentType = io.github.landwarderer.futon.mihon.parsers.model.ContentType.OTHER
        }
    )
}

fun ContentSource.toMangaSource(): MangaSource {
    return this
}

