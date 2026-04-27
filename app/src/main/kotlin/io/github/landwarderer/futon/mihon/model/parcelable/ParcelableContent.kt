package io.github.landwarderer.futon.mihon.model.parcelable

import android.os.Parcel
import android.os.Parcelable
import io.github.landwarderer.futon.core.util.ext.readParcelableCompat
import io.github.landwarderer.futon.core.util.ext.readSerializableCompat
import io.github.landwarderer.futon.core.util.ext.readStringSet
import io.github.landwarderer.futon.core.util.ext.writeStringSet
import io.github.landwarderer.futon.mihon.model.contentSource
import io.github.landwarderer.futon.mihon.parsers.model.Content
import io.github.landwarderer.futon.mihon.parsers.model.ContentChapter
import io.github.landwarderer.futon.mihon.parsers.model.ContentRating
import io.github.landwarderer.futon.mihon.parsers.model.ContentState
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize

@Parcelize
data class ParcelableContent(
	val manga: Content,
	private val withDescription: Boolean = true,
	private val withChapters: Boolean = false,
) : Parcelable {

	companion object : Parceler<ParcelableContent> {

		override fun ParcelableContent.write(parcel: Parcel, flags: Int): Unit = with(manga) {
			parcel.writeLong(id)
			parcel.writeString(title)
			parcel.writeStringSet(altTitles)
			parcel.writeString(url)
			parcel.writeString(Url)
			parcel.writeFloat(rating)
			parcel.writeSerializable(contentRating)
			parcel.writeString(coverUrl)
			parcel.writeString(largeCoverUrl)
			parcel.writeString(description.takeIf { withDescription })
			parcel.writeParcelable(ParcelableContentTags(tags), flags)
			parcel.writeSerializable(state)
			parcel.writeStringSet(authors)
			parcel.writeString(source.name)
			// Write chapters if requested
			val chaptersToWrite = if (withChapters) chapters else null
			parcel.writeInt(chaptersToWrite?.size ?: -1)
			chaptersToWrite?.forEach { chapter ->
				parcel.writeLong(chapter.id)
				parcel.writeString(chapter.title)
				parcel.writeFloat(chapter.number)
				parcel.writeInt(chapter.volume)
				parcel.writeString(chapter.url)
				parcel.writeString(chapter.scanlator)
				parcel.writeLong(chapter.uploadDate)
				parcel.writeString(chapter.branch)
			}
		}

		override fun create(parcel: Parcel): ParcelableContent {
			val id = parcel.readLong()
			val title = requireNotNull(parcel.readString())
			val altTitles = parcel.readStringSet()
			val url = requireNotNull(parcel.readString())
			val Url = requireNotNull(parcel.readString())
			val rating = parcel.readFloat()
			val contentRating = parcel.readSerializableCompat<ContentRating>()
			val coverUrl = parcel.readString()
			val largeCoverUrl = parcel.readString()
			val description = parcel.readString()
			val tags = requireNotNull(parcel.readParcelableCompat<ParcelableContentTags>()).tags
			val state = parcel.readSerializableCompat<ContentState>()
			val authors = parcel.readStringSet()
			val sourceName = requireNotNull(parcel.readString())
			
			// Read chapters if present
			val chaptersSize = parcel.readInt()
			val chapters = if (chaptersSize >= 0) {
				List(chaptersSize) {
                    ContentChapter(
                        id = parcel.readLong(),
                        title = parcel.readString(),
                        number = parcel.readFloat(),
                        volume = parcel.readInt(),
                        url = requireNotNull(parcel.readString()),
                        scanlator = parcel.readString(),
                        uploadDate = parcel.readLong(),
                        branch = parcel.readString(),
                        source = contentSource(sourceName),
                    )
				}
			} else {
				null
			}
			
			return ParcelableContent(
				Content(
					id = id,
					title = title,
					altTitles = altTitles,
					url = url,
					Url = Url,
					rating = rating,
					contentRating = contentRating,
					coverUrl = coverUrl,
					largeCoverUrl = largeCoverUrl,
					description = description,
					tags = tags,
					state = state,
					authors = authors,
					chapters = chapters,
					source = contentSource(sourceName),
				),
				withDescription = true,
				withChapters = chapters != null,
			)
		}
	}
}

