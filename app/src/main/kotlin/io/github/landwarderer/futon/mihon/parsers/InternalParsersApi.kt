package io.github.landwarderer.futon.mihon.parsers

/**
 * This marker distinguishes the internal API and is used to opt-in for that feature when parsers developing.
 *
 * Any usage of a declaration annotated with `@InternalParsersApi` must be accepted either by
 * annotating that usage with the [OptIn] annotation, e.g. `@OptIn(InternalParsersApi::class)`,
 * or by using the compiler argument `-opt-in=io.github.landwarderer.futon.mihon.parsers.InternalParsersApi`.
 */
@Retention(AnnotationRetention.BINARY)
@SinceKotlin("1.3")
@RequiresOptIn
@MustBeDocumented
annotation class InternalParsersApi
