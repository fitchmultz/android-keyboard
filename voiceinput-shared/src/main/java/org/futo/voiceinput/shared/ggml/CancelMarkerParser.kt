package org.futo.voiceinput.shared.ggml

sealed class CancelMarkerResult {
    object NotCancelled : CancelMarkerResult()
    object CancelledByFlag : CancelMarkerResult()
    data class BailedLanguage(val language: String) : CancelMarkerResult()
    data class UnknownFormat(val raw: String) : CancelMarkerResult()
}

private const val CANCEL_MARKER = "<>CANCELLED<>"
private const val LANG_PREFIX = "lang="

fun parseCancelMarker(raw: String): CancelMarkerResult {
    val text = raw.trim()
    if (!text.contains(CANCEL_MARKER)) {
        return CancelMarkerResult.NotCancelled
    }

    val langIndex = text.indexOf(LANG_PREFIX)
    if (langIndex != -1) {
        val after = text.substring(langIndex + LANG_PREFIX.length)
        val lang = after.takeWhile { !it.isWhitespace() }.ifBlank { null }
        return if (lang != null) {
            CancelMarkerResult.BailedLanguage(lang)
        } else {
            CancelMarkerResult.UnknownFormat(raw)
        }
    }

    if (text.contains("flag")) {
        return CancelMarkerResult.CancelledByFlag
    }

    return CancelMarkerResult.UnknownFormat(raw)
}
