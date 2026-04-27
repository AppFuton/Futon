@file:JvmName("EnumUtils")

package io.github.landwarderer.futon.mihon.parsers.util

import kotlin.enums.EnumEntries

public fun <E : Enum<E>> EnumEntries<E>.names(): Array<String> = Array(size) { i ->
	get(i).name
}

public fun <E : Enum<E>> EnumEntries<E>.find(name: String): E? {
	return find { x -> x.name == name }
}
