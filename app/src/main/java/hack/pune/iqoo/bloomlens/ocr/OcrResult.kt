package hack.pune.iqoo.bloomlens.ocr

data class OcrResult(
    val rawText: String,
    val blocks: List<String>,
) {
    val isBlank: Boolean get() = rawText.isBlank()
}
