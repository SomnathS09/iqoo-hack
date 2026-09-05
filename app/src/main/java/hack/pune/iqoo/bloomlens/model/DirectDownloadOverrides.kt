package hack.pune.iqoo.bloomlens.model

/**
 * Direct-download URLs for specific AI Hub model releases whose GenieX-driven pull is known to
 * be broken. Verified on-device 2026-09-05: GenieX's downloader for qwen3_4b_instruct_2507
 * v0.52.0 requests a Range ending far short of the real object (its resolved total, ~1.19GB,
 * doesn't match the S3 object's actual Content-Length of ~2.54GB), so it always downloads a
 * truncated, undecodable archive. The S3 object itself is intact and correctly range-addressable
 * (confirmed via a manual probe), so downloading it directly - deriving size from the real HTTP
 * response rather than GenieX's manifest - and importing via HubSource.LOCALFS works around it.
 */
object DirectDownloadOverrides {
    val urls: Map<String, String> = mapOf(
        "Qwen3-4B-Instruct-2507" to
            "https://qaihub-public-assets.s3.us-west-2.amazonaws.com/qai-hub-models/models/" +
            "qwen3_4b_instruct_2507/releases/v0.52.0/" +
            "qwen3_4b_instruct_2507-genie-w4a16-qualcomm_snapdragon_8_elite_gen5.zip",
    )
}
