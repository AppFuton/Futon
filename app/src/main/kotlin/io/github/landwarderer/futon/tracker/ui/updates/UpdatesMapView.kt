package io.github.landwarderer.futon.tracker.ui.updates

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.model.getTitle
import io.github.landwarderer.futon.core.util.ext.getThemeColor
import io.github.landwarderer.futon.databinding.ItemUpdatesMapShelfBinding
import io.github.landwarderer.futon.databinding.ItemUpdatesMapTileBinding
import io.github.landwarderer.futon.databinding.ViewUpdatesMapBinding
import io.github.landwarderer.futon.image.ui.CoverImageView
import io.github.landwarderer.futon.tracker.domain.TreemapRect
import io.github.landwarderer.futon.tracker.domain.UpdatesShelf
import io.github.landwarderer.futon.tracker.domain.UpdatesShelfWindow
import io.github.landwarderer.futon.tracker.domain.foldTiles
import io.github.landwarderer.futon.tracker.domain.model.MangaTracking
import io.github.landwarderer.futon.tracker.domain.mosaicGrid
import io.github.landwarderer.futon.tracker.domain.squarify
import org.koitharu.kotatsu.parsers.model.Manga
import kotlin.math.ceil
import kotlin.math.roundToInt
import androidx.appcompat.R as appcompatR
import com.google.android.material.R as materialR

private const val MIN_TILE_DP = 44
private const val LABEL_MIN_WIDTH_DP = 70
private const val LABEL_MIN_HEIGHT_DP = 40
private const val GROUP_LABEL_DP = 22
private const val GROUP_INSET_DP = 4
private const val ZOOM_DURATION_MS = 220L
private const val ZOOM_IN_FROM_SCALE = 0.6f
private const val ZOOM_OUT_FROM_SCALE = 0.92f
private const val ZOOM_FROM_ALPHA = 0.4f

/** Manga with new chapters as a treemap sized by how many each has, where "+N" tiles zoom in on what they fold. */
class UpdatesMapView(
	context: Context,
	private val onOpen: (Manga) -> Unit,
	private val onZoomChanged: (Boolean) -> Unit,
) : LinearLayout(context) {

	private data class Node(
		val id: String,
		val tracking: MangaTracking?,
		val title: String,
		val value: Double,
		val group: String,
		val weight: Double = value,
		val folded: List<Node> = emptyList(),
	)

	private data class Group(val name: String, val nodes: List<Node>, val value: Double)

	/** Remembers ids rather than tiles so that a level survives the list updating underneath it. */
	private data class ZoomLevel(val group: String, val mangaIds: Set<Long>)

	private val binding = ViewUpdatesMapBinding.inflate(LayoutInflater.from(context), this)
	private val selectedStroke = context.getThemeColor(appcompatR.attr.colorPrimary)
	private val captionColor = context.getThemeColor(materialR.attr.colorSurface, 0.94f)
	private val tiles = HashMap<String, MaterialCardView>()
	private val zoomStack = ArrayList<ZoomLevel>()
	private val shelfAdapter = ShelfAdapter()
	private val rebuildRunnable = Runnable { rebuild() }
	private var entries: List<MangaTracking> = emptyList()
	private var selectedId: String? = null
	private var nodesById: Map<String, Node> = emptyMap()
	private var total = 0.0

	val isZoomed: Boolean
		get() = zoomStack.isNotEmpty()

	init {
		orientation = VERTICAL
		binding.chipGroupBy.setOnCheckedStateChangeListener { _, _ ->
			selectedId = null
			rebuild()
		}
		binding.frameTiles.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
			if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
				// Rotating resizes the frame more than once as insets arrive: draw only the final size.
				binding.frameTiles.removeCallbacks(rebuildRunnable)
				binding.frameTiles.post(rebuildRunnable)
			}
		}
		binding.cardDetail.setOnClickListener {
			nodesById[selectedId]?.tracking?.let { onOpen(it.manga) }
		}
		binding.buttonZoomOut.setOnClickListener { zoomOut() }
		with(binding.recyclerViewShelf) {
			layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
			adapter = shelfAdapter
			itemAnimator = null
		}
	}

	fun setEntries(entries: List<MangaTracking>) {
		this.entries = entries
		rebuild()
	}

	fun setShelf(shelf: UpdatesShelf) {
		binding.layoutShelf.isVisible = shelf.manga.isNotEmpty()
		binding.textViewShelf.text = context.getString(
			when (shelf.window) {
				UpdatesShelfWindow.LAST_CHECK -> R.string.updates_shelf_label_last_check
				UpdatesShelfWindow.LAST_24_HOURS -> R.string.updates_shelf_label_last_24_hours
			},
			shelf.manga.size,
		)
		shelfAdapter.submit(shelf.manga)
	}

	fun setBottomInset(inset: Int) {
		updatePadding(bottom = inset)
	}

	fun zoomOut() {
		if (zoomStack.isEmpty()) {
			return
		}
		zoomStack.removeAt(zoomStack.lastIndex)
		selectedId = null
		rebuild()
		val frame = binding.frameTiles
		animateZoom(frame.width / 2f, frame.height / 2f, ZOOM_OUT_FROM_SCALE)
	}

	private fun zoomIn(node: Node, tile: View) {
		zoomStack += ZoomLevel(node.group, node.folded.mapNotNullTo(HashSet()) { it.tracking?.manga?.id })
		selectedId = null
		rebuild()
		animateZoom(tile.x + tile.width / 2f, tile.y + tile.height / 2f, ZOOM_IN_FROM_SCALE)
	}

	private fun animateZoom(pivotX: Float, pivotY: Float, fromScale: Float) {
		val frame = binding.frameTiles
		frame.animate().cancel()
		frame.pivotX = pivotX
		frame.pivotY = pivotY
		frame.scaleX = fromScale
		frame.scaleY = fromScale
		frame.alpha = ZOOM_FROM_ALPHA
		frame.animate()
			.scaleX(1f)
			.scaleY(1f)
			.alpha(1f)
			.setDuration(ZOOM_DURATION_MS)
			.setInterpolator(DecelerateInterpolator())
			.start()
	}

	private fun groupName(tracking: MangaTracking): String = if (binding.chipGroupBy.checkedChipId == R.id.chip_by_genre) {
		tracking.manga.tags.firstOrNull()?.title ?: context.getString(R.string.unknown)
	} else {
		tracking.manga.source.getTitle(context)
	}

	private fun format(value: Double): String = context.getString(R.string.updates_map_new_count, value.toInt())

	private fun rebuild() {
		val frame = binding.frameTiles
		frame.removeAllViews()
		tiles.clear()
		val width = frame.width.toFloat()
		val height = frame.height.toFloat()
		if (width <= 0f || height <= 0f) {
			return
		}
		val nodes = entries.mapNotNull { tracking ->
			val value = tracking.newChapters.toDouble()
			if (value <= 0.0) {
				null
			} else {
				Node(tracking.manga.id.toString(), tracking, tracking.manga.title, value, groupName(tracking))
			}
		}
		total = nodes.sumOf { it.value }
		val available = nodes.mapNotNullTo(HashSet()) { it.tracking?.manga?.id }
		while (zoomStack.isNotEmpty() && zoomStack.last().mangaIds.none { it in available }) {
			zoomStack.removeAt(zoomStack.lastIndex)
		}
		val placed = HashMap<String, Node>()
		val zoom = zoomStack.lastOrNull()
		if (zoom == null) {
			placeGroups(frame, nodes, width, height, placed)
		} else {
			placeZoomed(frame, zoom, nodes, width, height, placed)
		}
		nodesById = placed
		if (placed[selectedId]?.tracking == null) {
			selectedId = placed.values.filter { it.tracking != null }.maxByOrNull { it.value }?.id
		}
		binding.textViewEmpty.isVisible = placed.isEmpty()
		// Keeps its space when hidden: selection depends on the size of the frame, so this must not resize it.
		binding.cardDetail.isInvisible = selectedId == null
		binding.scrollViewBy.isVisible = zoom == null
		binding.layoutZoom.isVisible = zoom != null
		if (zoom != null) {
			binding.textViewZoom.text = context.getString(R.string.updates_map_more, zoom.mangaIds.size, zoom.group)
		}
		updateSelection()
		onZoomChanged(isZoomed)
	}

	private fun placeGroups(frame: FrameLayout, nodes: List<Node>, width: Float, height: Float, placed: MutableMap<String, Node>) {
		val groups = nodes.groupBy { it.group }.map { (name, items) -> Group(name, items, items.sumOf { it.value }) }
		for ((group, rect) in squarify(groups, 0f, 0f, width, height) { it.value }) {
			addGroupLabel(frame, group, rect)
			val inset = dp(GROUP_INSET_DP).toFloat()
			val labelHeight = dp(GROUP_LABEL_DP).toFloat()
			val innerWidth = rect.width - inset * 2f
			val innerHeight = rect.height - labelHeight - inset
			if (innerWidth <= 0f || innerHeight <= 0f) {
				continue
			}
			val tileNodes = foldNodes(group.name, group.nodes, innerWidth, innerHeight, isZoomed = false)
			for ((node, nodeRect) in squarify(tileNodes, rect.x + inset, rect.y + labelHeight, innerWidth, innerHeight) { it.weight }) {
				placed[node.id] = node
				addTile(frame, node, nodeRect)
			}
		}
	}

	private fun placeZoomed(
		frame: FrameLayout,
		zoom: ZoomLevel,
		nodes: List<Node>,
		width: Float,
		height: Float,
		placed: MutableMap<String, Node>,
	) {
		val visible = nodes.filter { it.tracking?.manga?.id in zoom.mangaIds }
		for ((node, rect) in squarify(foldNodes(zoom.group, visible, width, height, isZoomed = true), 0f, 0f, width, height) { it.weight }) {
			placed[node.id] = node
			addTile(frame, node, rect)
		}
	}

	private fun foldNodes(group: String, nodes: List<Node>, width: Float, height: Float, isZoomed: Boolean): List<Node> {
		val sorted = nodes.sortedByDescending { it.value }
		val minArea = (dp(MIN_TILE_DP) * dp(MIN_TILE_DP)).toFloat()
		val fold = foldTiles(sorted.map { it.value }, width * height, minArea, isZoomed)
		val kept = sorted.take(fold.kept).let { items ->
			if (fold.isEqualSized) items.map { it.copy(weight = 1.0) } else items
		}
		val small = sorted.drop(fold.kept)
		if (small.isEmpty()) {
			return kept
		}
		return kept + Node(
			id = "more:$group",
			tracking = null,
			title = "+${small.size}",
			value = small.sumOf { it.value },
			group = group,
			weight = if (fold.isEqualSized) 1.0 else small.sumOf { it.weight },
			folded = small,
		)
	}

	private fun addGroupLabel(frame: FrameLayout, group: Group, rect: TreemapRect) {
		val outline = View(context)
		outline.setBackgroundResource(R.drawable.bg_updates_map_group)
		frame.addView(outline, frameParams(rect.x + 1f, rect.y + 1f, rect.width - 2f, rect.height - 2f))
		val label = LayoutInflater.from(context).inflate(R.layout.item_updates_map_label, frame, false) as TextView
		label.text = context.getString(R.string.updates_map_group, group.name, format(group.value))
		frame.addView(label, frameParams(rect.x + dp(8), rect.y + dp(2), rect.width - dp(16), dp(18).toFloat()))
	}

	private fun addTile(frame: FrameLayout, node: Node, rect: TreemapRect) {
		val tile = ItemUpdatesMapTileBinding.inflate(LayoutInflater.from(context), frame, false)
		val tileWidth = rect.width - 2f
		val tileHeight = rect.height - 2f
		val tracking = node.tracking
		tile.root.contentDescription = "${node.title}, ${format(node.value)}"
		tile.imageViewCover.isVisible = tracking != null
		tile.textViewMore.isVisible = tracking == null
		if (tracking != null) {
			tile.root.setOnClickListener {
				selectedId = node.id
				updateSelection()
			}
			tile.imageViewCover.setImageAsync(tracking.manga.coverUrl, tracking.manga)
		} else {
			tile.root.setOnClickListener { zoomIn(node, tile.root) }
			tile.textViewMore.text = node.title
			tile.layoutMosaic.isVisible = true
			addMosaic(tile.layoutMosaic, node.folded, tileWidth, tileHeight)
		}
		val hasLabel = tracking != null && tileWidth >= dp(LABEL_MIN_WIDTH_DP) && tileHeight >= dp(LABEL_MIN_HEIGHT_DP)
		tile.layoutCaption.isVisible = hasLabel
		if (hasLabel) {
			tile.layoutCaption.setBackgroundColor(captionColor)
			tile.textViewTitle.text = node.title
			tile.textViewValue.text = format(node.value)
		}
		frame.addView(tile.root, frameParams(rect.x + 1f, rect.y + 1f, tileWidth, tileHeight))
		tiles[node.id] = tile.root
	}

	private fun addMosaic(container: FrameLayout, covers: List<Node>, width: Float, height: Float) {
		val grid = mosaicGrid(covers.size, width, height)
		val cellWidth = width / grid.columns
		val cellHeight = height / grid.rows
		val gap = dp(1)
		covers.take(grid.columns * grid.rows).forEachIndexed { index, node ->
			val manga = node.tracking?.manga ?: return@forEachIndexed
			val column = index % grid.columns
			val row = index / grid.columns
			val cover = CoverImageView(container.context)
			cover.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
			container.addView(
				cover,
				FrameLayout.LayoutParams(
					ceil(cellWidth).toInt() - if (column < grid.columns - 1) gap else 0,
					ceil(cellHeight).toInt() - if (row < grid.rows - 1) gap else 0,
				).apply {
					leftMargin = (column * cellWidth).toInt()
					topMargin = (row * cellHeight).toInt()
				},
			)
			cover.setImageAsync(manga.coverUrl, manga)
		}
	}

	private fun frameParams(x: Float, y: Float, width: Float, height: Float) = FrameLayout.LayoutParams(
		width.roundToInt().coerceAtLeast(1),
		height.roundToInt().coerceAtLeast(1),
	).apply {
		leftMargin = x.roundToInt()
		topMargin = y.roundToInt()
	}

	private fun updateSelection() {
		for ((id, card) in tiles) {
			card.strokeColor = selectedStroke
			card.strokeWidth = if (id == selectedId) dp(2) else 0
		}
		val node = nodesById[selectedId] ?: return
		val manga = node.tracking?.manga ?: return
		binding.imageViewDetail.setImageAsync(manga.coverUrl, manga)
		binding.textViewDetailTitle.text = node.title
		binding.textViewDetailMeta.text = context.getString(
			R.string.updates_map_detail,
			node.group,
			format(node.value),
			(node.value / total * 100.0).roundToInt(),
		)
	}

	private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

	private inner class ShelfAdapter : RecyclerView.Adapter<ShelfHolder>() {

		private var manga: List<Manga> = emptyList()

		@SuppressLint("NotifyDataSetChanged")
		fun submit(items: List<Manga>) {
			if (items != manga) {
				manga = items
				notifyDataSetChanged()
			}
		}

		override fun getItemCount() = manga.size

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ShelfHolder(
			ItemUpdatesMapShelfBinding.inflate(LayoutInflater.from(parent.context), parent, false),
		)

		override fun onBindViewHolder(holder: ShelfHolder, position: Int) = holder.bind(manga[position])

		override fun onViewRecycled(holder: ShelfHolder) = holder.recycle()
	}

	private inner class ShelfHolder(
		private val item: ItemUpdatesMapShelfBinding,
	) : RecyclerView.ViewHolder(item.root) {

		fun bind(manga: Manga) {
			item.root.contentDescription = manga.title
			item.root.setOnClickListener { onOpen(manga) }
			item.imageViewCover.setImageAsync(manga.coverUrl, manga)
		}

		fun recycle() = item.imageViewCover.disposeImage()
	}
}
