package io.github.landwarderer.futon.novel.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelChapterContentTest {

	@Test
	fun testSanitizesScriptsAndEventHandlers() {
		val maliciousHtml = """
			<div>
				<h1>Title</h1>
				<script>alert('pwned');</script>
				<img src="https://example.com/cover.jpg" onerror="alert(1)" data-src="https://example.com/real.jpg" srcset="https://example.com/real.jpg 1x">
				<p onclick="stealCookies()">Paragraph content</p>
			</div>
		""".trimIndent()

		val sanitized = NovelChapterContent.toHtmlFragment(maliciousHtml, "https://example.com/chapter-1")

		assertFalse(sanitized.contains("<script>"))
		assertFalse(sanitized.contains("alert('pwned')"))
		assertFalse(sanitized.contains("onerror"))
		assertFalse(sanitized.contains("onclick"))
		assertTrue(sanitized.contains("data-src=\"https://example.com/real.jpg\""))
		assertTrue(sanitized.contains("srcset=\"https://example.com/real.jpg 1x\""))
		assertTrue(sanitized.contains("Paragraph content"))
	}
}

