package io.github.landwarderer.futon.mihon.model

import io.github.landwarderer.futon.mihon.parsers.model.ContentSource
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ContentSourceSerializer : KSerializer<ContentSource> {

    override val descriptor: SerialDescriptor = serialDescriptor<String>()

    override fun serialize(
        encoder: Encoder,
        value: ContentSource
    ) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): ContentSource = contentSource(decoder.decodeString())
}
