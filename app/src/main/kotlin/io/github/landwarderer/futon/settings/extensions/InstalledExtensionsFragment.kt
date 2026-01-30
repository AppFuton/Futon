package io.github.landwarderer.futon.settings.extensions

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.request.ImageRequest
import com.hannesdorfmann.adapterdelegates4.AdapterDelegate
import com.hannesdorfmann.adapterdelegates4.ListDelegationAdapter
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.ui.image.ImageViewTarget
import io.github.landwarderer.futon.core.ui.list.decor.SpacingItemDecoration
import io.github.landwarderer.futon.core.util.ext.enqueueWith
import io.github.landwarderer.futon.core.util.ext.resolveDp
import io.github.landwarderer.futon.databinding.FragmentInstalledExtensionsBinding
import io.github.landwarderer.futon.databinding.ItemInstalledExtensionBinding
import io.github.landwarderer.futon.list.ui.model.ListModel
import io.github.landwarderer.futon.list.ui.adapter.loadingStateAD
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class InstalledExtensionsFragment : Fragment() {

	@Inject
	lateinit var imageLoader: ImageLoader

	private val viewModel by viewModels<InstalledExtensionsViewModel>()

	private var _binding: FragmentInstalledExtensionsBinding? = null
	private val binding get() = _binding!!

	private val adapter by lazy {
		ListDelegationAdapter<List<ListModel>>(
			loadingStateAD(),
			installedExtensionItemDelegate(),
		)
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		_binding = FragmentInstalledExtensionsBinding.inflate(inflater, container, false)
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

		viewLifecycleOwner.lifecycleScope.launch {
			viewModel.installedExtensions.collectLatest { items ->
				adapter.items = items
				adapter.notifyDataSetChanged()

				binding.recyclerView.isVisible = items.isNotEmpty()
				binding.layoutEmpty.isVisible = items.isEmpty()
			}
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	private fun installedExtensionItemDelegate(): AdapterDelegate<List<ListModel>> {
		return adapterDelegateViewBinding<InstalledExtensionItem, ListModel, ItemInstalledExtensionBinding>(
			{ inflater, parent -> ItemInstalledExtensionBinding.inflate(inflater, parent, false) },
		) {
			binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
				viewModel.toggleExtensionEnabled(item.extension.pkgName, item.extension.isEnabled)
			}

			binding.buttonUninstall.setOnClickListener {
				viewModel.uninstallExtension(item.extension.pkgName)
			}

			bind {
				binding.textViewName.text = item.extension.name

				val sourcesText = item.extension.sources.joinToString(", ") { it.name }
				binding.textViewSources.text = getString(R.string.extension_sources) + ": " + sourcesText

				val versionText = getString(
					R.string.extension_sources_count,
					item.extension.sources.size
				) + " • " + item.extension.versionName
				binding.textViewVersion.text = versionText

				binding.chipNsfw.isVisible = item.extension.isNsfw

				binding.switchEnabled.isChecked = item.extension.isEnabled

				if (item.extension.icon != null) {
					binding.imageViewIcon.setImageDrawable(item.extension.icon as? Drawable)
				} else {
					binding.imageViewIcon.setImageResource(R.drawable.ic_placeholder)
				}
			}
		}
	}
}
