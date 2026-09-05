package hack.pune.iqoo.bloomlens.ui.rewards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hack.pune.iqoo.bloomlens.model.REWARD_CATALOG
import hack.pune.iqoo.bloomlens.model.Reward
import hack.pune.iqoo.bloomlens.ui.theme.HintPurple

@Composable
fun RewardsDialog(stars: Int, onRedeem: (Reward) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⭐ Rewards - $stars stars") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Complete a full Bloom's Taxonomy journey on a problem to earn a star.",
                    style = MaterialTheme.typography.bodySmall,
                )
                REWARD_CATALOG.forEach { reward ->
                    val affordable = stars >= reward.cost
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("${reward.emoji} ${reward.title}", style = MaterialTheme.typography.bodyMedium)
                            Text("${reward.cost} stars", style = MaterialTheme.typography.bodySmall, color = HintPurple)
                        }
                        TextButton(onClick = { onRedeem(reward) }, enabled = affordable) {
                            Text("Redeem")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
