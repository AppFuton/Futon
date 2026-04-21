package io.github.landwarderer.futon.mihon.model.parcelable

import android.os.Parcel
import io.github.landwarderer.futon.mihon.model.contentSource
import io.github.landwarderer.futon.mihon.parsers.model.ContentSource
import kotlinx.parcelize.Parceler

class ContentSourceParceler : Parceler<ContentSource> {

	override fun create(parcel: Parcel): ContentSource = contentSource(parcel.readString())

	override fun ContentSource.write(parcel: Parcel, flags: Int) {
		parcel.writeString(name)
	}
}

