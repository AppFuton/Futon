package io.github.landwarderer.futon.mihon.parsers.exception

import io.github.landwarderer.futon.mihon.parsers.InternalParsersApi
import io.github.landwarderer.futon.mihon.parsers.util.json.mapJSONNotNull
import okio.IOException
import org.json.JSONArray

public class GraphQLException @InternalParsersApi constructor(errors: JSONArray) : IOException() {

	public val messages: List<String> = errors.mapJSONNotNull {
		it.getString("message")
	}

	override val message: String
		get() = messages.joinToString("\n")
}

