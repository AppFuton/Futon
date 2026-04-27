package io.github.landwarderer.futon.mihon.model.parcelable

import android.os.Parcel
import android.os.Parcelable
import io.github.landwarderer.futon.mihon.model.contentSource
import io.github.landwarderer.futon.mihon.parsers.model.ContentChapter
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize

@Parcelize
data class ParcelableChapter(
	val chapter: ContentChapter,
) : Parcelable {

	companion object : Parceler<ParcelableChapter> {

		override fun create(parcel: Parcel) = ParcelableChapter(
			ContentChapter(
				id = parcel.readLong(),
				title = parcel.readString(),
				number = parcel.readFloat(),
				volume = parcel.readInt(),
				url = parcel.readString().orEmpty(),
				scanlator = parcel.readString(),
				uploadDate = parcel.readLong(),
				branch = parcel.readString(),
				source = contentSource(parcel.readString()),
			),
		)

		override fun ParcelableChapter.write(parcel: Parcel, flags: Int) = with(chapter) {
			parcel.writeLong(id)
			parcel.writeString(title)
			parcel.writeFloat(number)
			parcel.writeInt(volume)
			parcel.writeString(url)
			parcel.writeString(scanlator)
			parcel.writeLong(uploadDate)
			parcel.writeString(branch)
			parcel.writeString(source.name)
		}
	}
}

