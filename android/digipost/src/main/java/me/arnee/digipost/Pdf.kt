package me.arnee.digipost

import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream

object Pdf {
    private const val BREDDE = 595
    private const val HOYDE = 842
    private const val MARG = 64

    private fun lag(tekst: String, maling: TextPaint) =
        StaticLayout.Builder.obtain(tekst, 0, tekst.length, maling, BREDDE - 2 * MARG).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.2f).build()

    fun brev(avsender: Papir?, mottaker: Papir?, dato: String, emne: String, tekst: String): ByteArray {
        val dokument = PdfDocument()
        val vanlig = TextPaint().apply { textSize = 11f; isAntiAlias = true; color = android.graphics.Color.BLACK }
        val liten = TextPaint(vanlig).apply { textSize = 9f }
        val fet = TextPaint(vanlig).apply { textSize = 14f; typeface = Typeface.DEFAULT_BOLD }
        val topp = buildString {
            avsender?.let { appendLine(it.tekst().replace(", ", "\n")); appendLine() }
            mottaker?.let { appendLine(it.tekst().replace(", ", "\n")); appendLine() }
            append(dato)
        }
        val deler = listOf(lag(topp, liten), lag(emne, fet), lag(tekst, vanlig))
        var side = 0
        var sideobjekt: PdfDocument.Page? = null
        var y = 0f
        fun nySide() {
            sideobjekt?.let { dokument.finishPage(it) }
            side++
            sideobjekt = dokument.startPage(PdfDocument.PageInfo.Builder(BREDDE, HOYDE, side).create())
            y = MARG.toFloat()
        }
        nySide()
        deler.forEach { del ->
            for (linje in 0 until del.lineCount) {
                val hoyde = (del.getLineBottom(linje) - del.getLineTop(linje)).toFloat()
                if (y + hoyde > HOYDE - MARG) nySide()
                val lerret = sideobjekt!!.canvas
                lerret.save()
                lerret.translate(MARG.toFloat(), y - del.getLineTop(linje))
                lerret.clipRect(0f, del.getLineTop(linje).toFloat(), (BREDDE - 2 * MARG).toFloat(), del.getLineBottom(linje).toFloat())
                del.draw(lerret)
                lerret.restore()
                y += hoyde
            }
            y += 18f
        }
        sideobjekt?.let { dokument.finishPage(it) }
        val ut = ByteArrayOutputStream()
        dokument.writeTo(ut)
        dokument.close()
        return ut.toByteArray()
    }
}
