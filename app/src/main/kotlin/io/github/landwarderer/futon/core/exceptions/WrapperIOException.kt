package io.github.landwarderer.futon.core.exceptions

import okio.IOException

class WrapperIOException(override val cause: Exception) : IOException(cause)
