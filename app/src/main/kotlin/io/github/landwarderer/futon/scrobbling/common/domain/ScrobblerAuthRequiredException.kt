package io.github.landwarderer.futon.scrobbling.common.domain

import okio.IOException
import io.github.landwarderer.futon.scrobbling.common.domain.model.ScrobblerService

class ScrobblerAuthRequiredException(
	val scrobbler: ScrobblerService,
) : IOException()
