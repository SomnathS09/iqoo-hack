package hack.pune.iqoo.bloomlens.ui.onboarding

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hack.pune.iqoo.bloomlens.model.PersonaOptions

@Composable
fun OnboardingScreen(onComplete: (skillLevel: String, focusArea: String) -> Unit) {
    var skillLevel by remember { mutableStateOf<String?>(null) }
    var focusArea by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Welcome to BloomLens", style = MaterialTheme.typography.titleLarge)
        Text(
            "Let's personalize your tutoring - a couple of quick questions:",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )

        Text("What's your skill level?", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier
                .padding(top = 8.dp, bottom = 24.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PersonaOptions.SKILL_LEVELS.forEach { level ->
                FilterChip(selected = skillLevel == level, onClick = { skillLevel = level }, label = { Text(level) })
            }
        }

        Text("What do you want to focus on?", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier
                .padding(top = 8.dp, bottom = 32.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PersonaOptions.FOCUS_AREAS.forEach { area ->
                FilterChip(selected = focusArea == area, onClick = { focusArea = area }, label = { Text(area) })
            }
        }

        Button(
            onClick = { onComplete(skillLevel ?: "Beginner", focusArea ?: "General") },
            modifier = Modifier.fillMaxWidth(),
            enabled = skillLevel != null && focusArea != null,
        ) {
            Text("Start Learning")
        }
    }
}
