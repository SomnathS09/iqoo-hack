package hack.pune.iqoo.bloomlens.provider

import hack.pune.iqoo.bloomlens.llm.Flashcard
import hack.pune.iqoo.bloomlens.model.PersonaOptions
import hack.pune.iqoo.bloomlens.state.ChatEntry

/**
 * Plain server-rendered HTML for Provider Mode - no JS framework, no CDN assets, so it works on
 * any browser over the local hotspot with zero internet access.
 */
object ProviderHtml {
    private const val STYLE = """
        <style>
        * { box-sizing: border-box; }
        body { font-family: -apple-system, Roboto, Arial, sans-serif; margin: 0; padding: 16px; background: #f7f5fb; color: #1b1b1f; }
        h1 { font-size: 22px; margin: 0 0 4px; }
        .sub { color: #5a5a63; font-size: 14px; margin-bottom: 20px; }
        label { display: block; font-weight: 600; margin: 14px 0 6px; font-size: 14px; }
        input[type=text], select, textarea { width: 100%; padding: 12px; font-size: 16px; border-radius: 10px; border: 1px solid #ccc; font-family: inherit; }
        textarea { min-height: 70px; }
        input[type=file] { width: 100%; padding: 10px 0; }
        button, .btn { display: block; width: 100%; padding: 14px; font-size: 16px; font-weight: 600; border: none; border-radius: 12px; background: #00c853; color: #fff; margin-top: 18px; text-align: center; text-decoration: none; }
        .btn-secondary { background: #7c4dff; }
        .card { background: #fff; border-radius: 16px; padding: 14px 16px; margin: 12px 0; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
        .bubble-tutor { background: #ece6fb; }
        .bubble-user { background: #d9f7e8; margin-left: 15%; }
        .level { color: #7c4dff; font-weight: 700; font-size: 13px; margin-bottom: 6px; }
        .devil { background: #7a3b12; color: #fff; }
        .devil .level { color: #ffd699; }
        .error { background: #ffe3e3; color: #9b1c1c; padding: 12px; border-radius: 10px; margin-bottom: 12px; }
        .footer { text-align: center; color: #999; font-size: 12px; margin-top: 24px; }
        img.photo { width: 100%; border-radius: 12px; margin-bottom: 4px; }
        </style>
    """

    private fun page(title: String, body: String): String = """
        <!doctype html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>$title</title>
        $STYLE
        </head>
        <body>
        $body
        </body>
        </html>
    """.trimIndent()

    fun landingPage(error: String? = null): String {
        val skillOptions = PersonaOptions.SKILL_LEVELS.joinToString("") { "<option value=\"${escape(it)}\">${escape(it)}</option>" }
        val focusOptions = PersonaOptions.FOCUS_AREAS.joinToString("") { "<option value=\"${escape(it)}\">${escape(it)}</option>" }
        val errorHtml = error?.let { "<div class=\"error\">${escape(it)}</div>" }.orEmpty()
        return page(
            "BloomLens",
            """
            <h1>🌸 BloomLens</h1>
            <div class="sub">Point your camera at a problem, or type it in - your personal Bloom's Taxonomy tutor is running right on this teacher's phone. No internet needed.</div>
            $errorHtml
            <form method="post" action="/start" enctype="multipart/form-data">
                <label>Your name</label>
                <input type="text" name="name" placeholder="e.g. Priya" maxlength="40">

                <label>Your skill level</label>
                <select name="skillLevel">$skillOptions</select>

                <label>Focus area</label>
                <select name="focusArea">$focusOptions</select>

                <label>Your goal for this session (optional)</label>
                <input type="text" name="goal" placeholder="e.g. Fully understand this problem" maxlength="120">

                <label>Take or upload a photo of the problem</label>
                <input type="file" name="photo" accept="image/*" capture="environment">

                <label>...or type the problem directly</label>
                <textarea name="problemText" placeholder="Type or paste the problem here"></textarea>

                <button type="submit">Start Learning</button>
            </form>
            <div class="footer">Served locally from a nearby phone - no data leaves this network.</div>
            """.trimIndent(),
        )
    }

    fun chatPage(webSession: WebTutorSession): String {
        val bubbles = webSession.messages.joinToString("\n") { bubble(it) }
        val photoHtml = if (webSession.imagePath != null) {
            "<img class=\"photo\" src=\"/photo?sid=${webSession.id}\">"
        } else {
            ""
        }
        val footer = if (webSession.isComplete) {
            val levels = webSession.messages.mapNotNull { it.bloomLevel }.distinct().joinToString(" → ") { it.label }
            """
            <div class="card">
                <div class="level">Session complete 🎉</div>
                Levels covered: ${escape(levels)}
            </div>
            <a class="btn" href="/new">Scan a New Problem</a>
            """.trimIndent()
        } else {
            """
            <form method="post" action="/reply">
                <textarea name="answer" placeholder="Type your answer..." autofocus required></textarea>
                <button type="submit">Send</button>
            </form>
            <form method="post" action="/flashcards">
                <button type="submit" class="btn-secondary">📇 Quick Flashcards</button>
            </form>
            """.trimIndent()
        }
        return page(
            "BloomLens Chat",
            """
            <h1>🌸 ${escape(webSession.studentName)}'s Session</h1>
            <div class="sub">${escape(webSession.currentLevel.label)} - Bloom's step ${webSession.currentLevel.ordinal + 1} of 6</div>
            $photoHtml
            $bubbles
            $footer
            """.trimIndent(),
        )
    }

    fun flashcardsPage(cards: List<Flashcard>?): String {
        val cardsHtml = if (cards.isNullOrEmpty()) {
            "<div class=\"error\">Couldn't generate flashcards - try again.</div>"
        } else {
            cards.joinToString("\n") { "<div class=\"card\"><b>${escape(it.front)}</b><br>${escape(it.back)}</div>" }
        }
        return page(
            "Flashcards",
            """
            <h1>📇 Quick Flashcards</h1>
            $cardsHtml
            <a class="btn" href="/chat">Back to chat</a>
            """.trimIndent(),
        )
    }

    fun errorPage(message: String): String = page(
        "BloomLens",
        "<h1>⚠️ ${escape(message)}</h1><a class=\"btn\" href=\"/\">Go back</a>",
    )

    private fun bubble(entry: ChatEntry): String {
        val cardClass = when {
            !entry.fromTutor -> "card bubble-user"
            entry.isDevilsAdvocate -> "card devil"
            else -> "card bubble-tutor"
        }
        val levelHtml = if (entry.fromTutor && entry.bloomLevel != null) {
            val label = if (entry.isDevilsAdvocate) "😈 Devil's Advocate (${entry.bloomLevel.label})" else entry.bloomLevel.label
            "<div class=\"level\">${escape(label)}</div>"
        } else {
            ""
        }
        val text = escape(entry.text).replace("\n", "<br>")
        return "<div class=\"$cardClass\">$levelHtml$text</div>"
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
