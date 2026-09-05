package hack.pune.iqoo.bloomlens.model

data class Reward(val id: String, val title: String, val cost: Int, val emoji: String)

val REWARD_CATALOG = listOf(
    Reward("hint_pack", "Free Hint Pack", cost = 3, emoji = "💡"),
    Reward("flashcard_pack", "Bonus Flashcard Set", cost = 5, emoji = "🃏"),
    Reward("bronze_badge", "Bronze Scholar Badge", cost = 8, emoji = "🥉"),
    Reward("silver_badge", "Silver Scholar Badge", cost = 15, emoji = "🥈"),
    Reward("gold_badge", "Gold Scholar Badge", cost = 25, emoji = "🥇"),
)
