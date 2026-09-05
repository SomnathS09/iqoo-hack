package hack.pune.iqoo.bloomlens.ui.tutor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import hack.pune.iqoo.bloomlens.llm.BloomLevel
import hack.pune.iqoo.bloomlens.ui.theme.HintGreen
import hack.pune.iqoo.bloomlens.ui.theme.HintPurple

/** Shows the learner's position in the Remember -> Create ladder for the current problem. */
@Composable
fun BloomProgressBar(currentLevel: BloomLevel, isComplete: Boolean, modifier: Modifier = Modifier) {
    val levels = BloomLevel.entries
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            levels.forEachIndexed { index, level ->
                val done = isComplete || index < currentLevel.ordinal
                val current = !isComplete && level == currentLevel
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                done -> HintGreen
                                current -> HintPurple
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                )
                if (index != levels.lastIndex) Spacer(modifier = Modifier.width(4.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            if (isComplete) {
                "Journey complete - all ${levels.size} levels"
            } else {
                "${currentLevel.label} - step ${currentLevel.ordinal + 1} of ${levels.size}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
