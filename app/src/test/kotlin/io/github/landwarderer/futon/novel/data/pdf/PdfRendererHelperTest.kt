package io.github.landwarderer.futon.novel.data.pdf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File

class PdfRendererHelperTest {

	@Test
	fun testInvalidFileGracefulHandling() {
		val helper = PdfRendererHelper(File("/invalid/non_existent.pdf"))
		assertEquals(0, helper.pageCount)
		assertFalse(helper.isValid)
		assertNull(helper.renderPage(0))
		assertNull(helper.renderThumbnail(0))
		helper.close()
	}

	@Test
	fun testUriConstructorGracefulHandling() {
		val context = mock(Context::class.java)
		val resolver = mock(ContentResolver::class.java)
		val uri = mock(Uri::class.java)
		`when`(context.contentResolver).thenReturn(resolver)
		`when`(resolver.openFileDescriptor(uri, "r")).thenReturn(null)

		val helper = PdfRendererHelper(context, uri)
		assertEquals(0, helper.pageCount)
		assertFalse(helper.isValid)
		assertNull(helper.renderPage(0))
		helper.close()
	}
}
