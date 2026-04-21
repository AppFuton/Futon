package io.github.landwarderer.futon.mihon.extensions.repo

data class RepoAvailableExtension(
	val type: ExternalExtensionType,
	val name: String,
	val pkgName: String,
	val versionName: String,
	val versionCode: Long,
	val libVersion: Double,
	val lang: String,
	val isNsfw: Boolean,
	val sourceNames: List<String>,
	val apkName: String,
	val iconUrl: String,
	val repoUrl: String,
	val repoName: String,
	val signatureHash: String,
	val isCompatible: Boolean,
)
