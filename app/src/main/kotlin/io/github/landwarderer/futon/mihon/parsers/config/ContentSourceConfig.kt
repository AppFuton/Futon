package io.github.landwarderer.futon.mihon.parsers.config

interface ContentSourceConfig {
	operator fun <T> get(key: ConfigKey<T>): T
}
