package io.github.landwarderer.futon.core.ui.image

import android.graphics.drawable.Drawable
import android.widget.ImageView
import coil3.target.GenericViewTarget

class ImageViewTarget(override val view: ImageView) : GenericViewTarget<ImageView>() {

	override var drawable: Drawable?
		get() = view.drawable
		set(value) {
			view.setImageDrawable(value)
		}
}
