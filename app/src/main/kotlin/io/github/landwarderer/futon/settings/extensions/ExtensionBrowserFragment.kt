package io.github.landwarderer.futon.settings.extensions

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.request.ImageRequest
import com.hannesdorfmann.adapterdelegates4.AdapterDelegate
import io.github.landwarderer.futon.core.ui.image.ImageViewTarget
import io.github.landwarderer.futon.core.util.ext.enqueueWith
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.ui.list.OnListItemClickListener
import io.github.landwarderer.futon.core.ui.list.decor.SpacingItemDecoration
import io.github.landwarderer.futon.core.util.ext.resolveDp
import io.github.landwarderer.futon.databinding.FragmentExtensionBrowserBinding
import io.github.landwarderer.futon.databinding.ItemExtensionBinding
import io.github.landwarderer.futon.list.ui.adapter.ListStateHolderListener
import io.github.landwarderer.futon.list.ui.adapter.TypedListSpacingDecoration
import io.github.landwarderer.futon.list.ui.model.ListModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ExtensionBrowserFragment : Fragment(), OnListItemClickListener<ExtensionBrowserItem> {

	@Inject
	lateinit var imageLoader: ImageLoader

	private val viewModel by viewModels<ExtensionBrowserViewModel>()

	private var _binding: FragmentExtensionBrowserBinding? = null
	private val binding get() = _binding!!

	private val adapter by lazy {
		val delegate = extensionItemDelegate()
		com.hannesdorfmann.adapterdelegates4.ListDelegationAdapter<List<ListModel>>(
			delegate,
		)
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		_binding = FragmentExtensionBrowserBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		binding.toolbar.setNavigationOnClickListener {
			parentFragmentManager.popBackStack()
		}

		binding.recyclerView.adapter = adapter
		binding.recyclerView.addItemDecoration(
			SpacingItemDecoration(resources.resolveDp(8), withBottomPadding = true),
		)

		binding.swipeRefreshLayout.setOnRefreshListener {
			viewModel.loadExtensions()
		}

		binding.searchBar.addTextChangedListener { text ->
			viewModel.setSearchQuery(text?.toString().orEmpty())
		}

		binding.fabFilter.setOnClickListener {
			viewModel.toggleNsfw()
		}

		viewLifecycleOwner.lifecycleScope.launch {
			viewModel.availableExtensions.collectLatest { items ->
				adapter.items = items
				adapter.notifyDataSetChanged()
				binding.swipeRefreshLayout.isRefreshing = false
			}
		}

		viewLifecycleOwner.lifecycleScope.launch {
			viewModel.showNsfw.collectLatest { showNsfw ->
				binding.fabFilter.text = if (showNsfw) {
					getString(R.string.show_nsfw_extensions)
				} else {
					getString(R.string.show_nsfw_extensions)
				}
				binding.fabFilter.icon = if (showNsfw) {
					requireContext().getDrawable(R.drawable.ic_eye)
				} else {
					requireContext().getDrawable(R.drawable.ic_eye_off)
				}
			}
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	override fun onItemClick(item: ExtensionBrowserItem, view: View) {
		val apkUrl = "${io.github.landwarderer.futon.core.parser.tachiyomi.TachiyomiExtensionIndexParser.APK_BASE_URL}/${item.metadata.apkName}"
		
		val intent = Intent(Intent.ACTION_VIEW).apply {
			data = Uri.parse(apkUrl)
		}

		try {
			startActivity(intent)
		} catch (e: Exception) {
			Toast.makeText(
				requireContext(),
				getString(R.string.extension_download_instruction),
				Toast.LENGTH_LONG,
			).show()
		}
	}

	private fun extensionItemDelegate(): AdapterDelegate<List<ListModel>> {
		return adapterDelegateViewBinding<ExtensionBrowserItem, ListModel, ItemExtensionBinding>(
			{ inflater, parent -> ItemExtensionBinding.inflate(inflater, parent, false) },
		) {
			binding.buttonAction.setOnClickListener {
				onItemClick(item, it)
			}

			bind {
				binding.textViewName.text = item.metadata.name.removePrefix("Tachiyomi: ")
				
				val sourcesText = item.metadata.sources.joinToString(", ") { it.name }
				binding.textViewSources.text = getString(R.string.extension_sources) + ": " + sourcesText

				val versionText = "${item.metadata.versionName} • ${item.metadata.lang} • ${item.metadata.sources.size} sources"
				binding.textViewVersion.text = versionText

				binding.chipNsfw.isVisible = item.metadata.isNsfw

				binding.buttonAction.text = if (item.isInstalled) {
					getString(R.string.extension_installed_version)
				} else {
					getString(R.string.extension_download)
				}
				binding.buttonAction.isEnabled = !item.isInstalled

			if (item.metadata.iconUrl?.isNotEmpty() == true) {
				ImageRequest.Builder(context)
					.data(item.metadata.iconUrl)
					.target(ImageViewTarget(binding.imageViewIcon))
					.enqueueWith(imageLoader)
			}
			}
		}
	}
}
