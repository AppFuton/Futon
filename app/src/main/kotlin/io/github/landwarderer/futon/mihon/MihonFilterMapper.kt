
package io.github.landwarderer.futon.mihon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import io.github.landwarderer.futon.mihon.parsers.InternalParsersApi
import io.github.landwarderer.futon.mihon.parsers.model.ContentListFilter
import io.github.landwarderer.futon.mihon.parsers.model.ContentListFilterOptions
import io.github.landwarderer.futon.mihon.parsers.model.ContentSource
import io.github.landwarderer.futon.mihon.parsers.model.ContentTag
import io.github.landwarderer.futon.mihon.parsers.model.ContentTagGroup
import io.github.landwarderer.futon.mihon.parsers.util.mapToSet

@OptIn(InternalParsersApi::class)
object MihonFilterMapper {

    private const val TAG = "MihonFilterMapper"
    private const val PREFIX_TOP = "top:"
    private const val PREFIX_SORT = "sort:"
    private const val PREFIX_TEXT = "text:"

    fun mapOptions(mihonFilters: FilterList, source: ContentSource): ContentListFilterOptions {
        val tagGroups = mutableListOf<ContentTagGroup>()
        var currentHeader = "General"

        mihonFilters.forEachIndexed { index, filter ->
            when (filter) {
                is Filter.Header -> {
                    currentHeader = filter.name
                }
                is Filter.Separator -> { }
                is Filter.Group<*> -> {
                    when (val state = filter.state) {
                        is List<*> -> {
                            val checkboxTags = mutableListOf<ContentTag>()
                            
                            state.forEach { subItem ->
                                if (subItem is Filter<*>) {
                                    when (subItem) {
                                        is Filter.Select<*> -> {
                                            val selectTags = mapFilterToTags(subItem, filter.name, source)
                                            if (selectTags.isNotEmpty()) {
                                                val groupTitle = "${filter.name} - ${subItem.name}"
                                                tagGroups.add(ContentTagGroup(groupTitle, selectTags.toSet()))
                                            }
                                        }
                                        is Filter.Sort -> {
                                            val sortTags = mapFilterToTags(subItem, filter.name, source)
                                            if (sortTags.isNotEmpty()) {
                                                val groupTitle = "${filter.name} - ${subItem.name}"
                                                tagGroups.add(ContentTagGroup(groupTitle, sortTags.toSet()))
                                            }
                                        }
                                        is Filter.Group<*> -> {
                                            val nestedTags = mapFilterToTags(subItem, filter.name, source)
                                            checkboxTags.addAll(nestedTags)
                                        }
                                        else -> {
                                            val tags = mapFilterToTags(subItem, filter.name, source)
                                            checkboxTags.addAll(tags)
                                        }
                                    }
                                }
                            }
                            
                            if (checkboxTags.isNotEmpty()) {
                                tagGroups.add(ContentTagGroup(filter.name, checkboxTags.toSet()))
                            }
                        }
                    }
                }
                else -> {
                    val tags = mapFilterToTags(filter, null, source)
                    if (tags.isNotEmpty()) {
                        tagGroups.add(ContentTagGroup(currentHeader, tags.toSet()))
                    }
                }
            }
        }
        
        val mergedGroups = tagGroups.groupBy { it.title }.map { (title, groups) ->
            val allTags = groups.flatMap { it.tags }.toSet()
            ContentTagGroup(title, allTags)
        }
        
        return ContentListFilterOptions(
            availableTags = mergedGroups.flatMap { it.tags }.toSet(),
            tagGroups = mergedGroups
        )
    }

    private fun mapFilterToTags(
        filter: Filter<*>, 
        parentName: String?, 
        source: ContentSource,
    ): List<ContentTag> {
        val prefix = if (parentName != null) "$parentName/" else PREFIX_TOP
        
        return when (filter) {
            is Filter.CheckBox -> {
                listOf(ContentTag(filter.name, "$prefix${filter.name}", source))
            }
            is Filter.TriState -> {
                listOf(ContentTag(filter.name, "$prefix${filter.name}", source))
            }
            is Filter.Select<*> -> {
                filter.values.map { value ->
                    val title = value.toString()
                    ContentTag(title, "$prefix${filter.name}/$title", source)
                }
            }
            is Filter.Sort -> {
                filter.values.map { value ->
                    ContentTag(value, "$PREFIX_SORT$prefix${filter.name}/$value", source)
                }
            }
            is Filter.Text -> {
                listOf(ContentTag(
                    title = "📝 ${filter.name}",
                    key = "$PREFIX_TEXT$prefix${filter.name}",
                    source = source
                ))
            }
            is Filter.Group<*> -> {
                val nestedTags = mutableListOf<ContentTag>()
                (filter.state as? List<*>)?.forEach { subItem ->
                    if (subItem is Filter<*>) {
                        val nestedPrefix = if (parentName != null) "$parentName/${filter.name}" else filter.name
                        nestedTags.addAll(mapFilterToTags(subItem, nestedPrefix, source))
                    }
                }
                nestedTags
            }
            else -> emptyList()
        }
    }

    fun updateMihonFilters(mihonFilters: FilterList, contentListFilter: ContentListFilter) {
        val selectedTags = contentListFilter.tags.mapToSet { it.key }
        val excludedTags = contentListFilter.tagsExclude.mapToSet { it.key }
        
        mihonFilters.forEach { filter ->
            when (filter) {
                is Filter.Group<*> -> {
                    (filter.state as? List<*>)?.forEach { subItem ->
                        val sub = subItem as? Filter<*> ?: return@forEach
                        updateSingleFilter(sub, filter.name, selectedTags, excludedTags)
                    }
                }
                else -> {
                    updateSingleFilter(filter, null, selectedTags, excludedTags)
                }
            }
        }
    }

    private fun updateSingleFilter(filter: Filter<*>, parentName: String?, selectedTags: Set<String>, excludedTags: Set<String>) {
        val prefix = if (parentName != null) "$parentName/" else PREFIX_TOP
        when (filter) {
            is Filter.CheckBox -> {
                val key = "$prefix${filter.name}"
                filter.state = key in selectedTags
            }
            is Filter.TriState -> {
                val key = "$prefix${filter.name}"
                filter.state = when {
                    key in selectedTags -> Filter.TriState.STATE_INCLUDE
                    key in excludedTags -> Filter.TriState.STATE_EXCLUDE
                    else -> Filter.TriState.STATE_IGNORE
                }
            }
            is Filter.Select<*> -> {
                filter.values.forEachIndexed { index, value ->
                    val key = "$prefix${filter.name}/$value"
                    if (key in selectedTags) {
                        filter.state = index
                    }
                }
            }
            is Filter.Sort -> {
                filter.values.forEachIndexed { index, value ->
                    val key = "$PREFIX_SORT$prefix${filter.name}/$value"
                    if (key in selectedTags) {
                        filter.state = Filter.Sort.Selection(index, filter.state?.ascending ?: false)
                    }
                }
            }
            is Filter.Text -> {
                val baseKey = "$PREFIX_TEXT$prefix${filter.name}"
                val matchingTag = selectedTags.find { it.startsWith(baseKey) }
                if (matchingTag != null) {
                    val value = if (matchingTag.contains("=")) {
                        matchingTag.substringAfter("=")
                    } else {
                        ""
                    }
                    filter.state = value
                }
            }
            is Filter.Group<*> -> {
                (filter.state as? List<*>)?.forEach { subItem ->
                    if (subItem is Filter<*>) {
                        val nestedPrefix = if (parentName != null) "$parentName/${filter.name}" else filter.name
                        updateSingleFilter(subItem, nestedPrefix, selectedTags, excludedTags)
                    }
                }
            }
            is Filter.Header, is Filter.Separator -> { }
            else -> {}
        }
    }
}
