package io.github.landwarderer.futon.core.util

interface CloseableSequence<T> : Sequence<T>, AutoCloseable
