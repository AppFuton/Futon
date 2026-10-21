package io.github.landwarderer.futon.onboarding.model

import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.landwarderer.futon.R
import androidx.appcompat.R as appcompatR
import com.google.android.material.R as materialR

enum class OnboardingPage(
	@DrawableRes val heroIconRes: Int? = null,
	@StringRes val badgeRes: Int,
	@StringRes val titleRes: Int,
	@StringRes val subtitleRes: Int,
	@StringRes val feature1Res: Int? = null,
	@StringRes val feature2Res: Int? = null,
	@StringRes val feature3Res: Int? = null,
	val isWarningPage: Boolean = false,
	@AttrRes val accentColorAttr: Int = appcompatR.attr.colorPrimary,
) {
	WELCOME(
		heroIconRes = R.drawable.ic_futon,
		badgeRes = R.string.onboarding_page1_badge,
		titleRes = R.string.onboarding_page1_title,
		subtitleRes = R.string.onboarding_page1_subtitle,
		feature1Res = R.string.onboarding_page1_feature1,
		feature2Res = R.string.onboarding_page1_feature2,
		feature3Res = R.string.onboarding_page1_feature3,
		accentColorAttr = appcompatR.attr.colorPrimary,
	),
	DUAL_MODES(
		heroIconRes = null,
		badgeRes = R.string.onboarding_page2_badge,
		titleRes = R.string.onboarding_page2_title,
		subtitleRes = R.string.onboarding_page2_subtitle,
		feature1Res = R.string.onboarding_page2_feature1,
		feature2Res = R.string.onboarding_page2_feature2,
		feature3Res = R.string.onboarding_page2_feature3,
		accentColorAttr = materialR.attr.colorTertiary,
	),
	SOURCES_AND_NSFW(
		heroIconRes = null,
		badgeRes = R.string.onboarding_page3_badge,
		titleRes = R.string.onboarding_page3_title,
		subtitleRes = R.string.onboarding_page3_subtitle,
		isWarningPage = true,
		accentColorAttr = appcompatR.attr.colorError,
	),
	GET_STARTED(
		heroIconRes = null,
		badgeRes = R.string.onboarding_page4_badge,
		titleRes = R.string.onboarding_page4_title,
		subtitleRes = R.string.onboarding_page4_subtitle,
		feature1Res = R.string.onboarding_page4_feature1,
		feature2Res = R.string.onboarding_page4_feature2,
		feature3Res = R.string.onboarding_page4_feature3,
		accentColorAttr = appcompatR.attr.colorPrimary,
	),
}
