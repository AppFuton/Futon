package io.github.landwarderer.futon.mihon.model.parcelable

import android.os.Parcel
import android.os.Parcelable
import io.github.landwarderer.futon.mihon.model.contentSource
import io.github.landwarderer.futon.mihon.parsers.model.ContentTag
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

object ContentTagParceler : Parceler<ContentTag> {
	override fun create(parcel: Parcel) = ContentTag(
		title = requireNotNull(parcel.readString()),
		key = requireNotNull(parcel.readString()),
		source = contentSource(parcel.readString()),
	)

	override fun ContentTag.write(parcel: Parcel, flags: Int) {
		parcel.writeString(title)
		parcel.writeString(key)
		parcel.writeString(source.name)
	}
}

@Parcelize
@TypeParceler<ContentTag, ContentTagParceler>
data class ParcelableContentTags(val tags: Set<ContentTag>) : Parcelable

