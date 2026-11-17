package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.SessionMode
import com.example.domain.prompt.SystemPromptPresets
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun CreateNewSessionDialog(
    hasActiveProject: Boolean,
    activeProjectName: String?,
    onStartSession: (
        mode: SessionMode,
        presetId: String,
        presetTitle: String,
        systemPrompt: String,
        attachProject: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMode by remember { mutableStateOf(SessionMode.NORMAL) }
    var selectedPreset by remember { mutableStateOf(SystemPromptPresets.PRESETS.first()) }
    var promptDraft by remember(selectedPreset.id) { mutableStateOf(selectedPreset.prompt) }
    var attachProjectInNormal by remember { mutableStateOf(false) }
    var attachProjectInSpecial by remember { mutableStateOf(hasActiveProject) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.AddComment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("New Session", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedMode == SessionMode.NORMAL,
                        onClick = { selectedMode = SessionMode.NORMAL },
                        leadingIcon = {
                            Icon(Icons.Rounded.QuestionAnswer, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = {
                            Text("Normal Q&A", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("session_mode_normal_chip")
                    )
                    FilterChip(
                        selected = selectedMode == SessionMode.SPECIAL,
                        onClick = { selectedMode = SessionMode.SPECIAL },
                        leadingIcon = {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = {
                            Text("Special Prompt", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("session_mode_special_chip")
                    )
                }

                if (selectedMode == SessionMode.NORMAL) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "General Questions & Live Internet",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "Lightweight assistant for everyday questions, explanations, troubleshooting, brainstorming, and live web searches without heavy scaffolding rules.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (hasActiveProject && !activeProjectName.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Include '$activeProjectName' files",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = attachProjectInNormal,
                                        onCheckedChange = { attachProjectInNormal = it }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Choose a starting role / system prompt:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SystemPromptPresets.PRESETS.forEach { preset ->
                            FilterChip(
                                selected = selectedPreset.id == preset.id,
                                onClick = { selectedPreset = preset },
                                label = { Text(preset.title, maxLines = 1) },
                                modifier = Modifier.testTag("session_preset_${preset.id}")
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "${selectedPreset.badge} • ${selectedPreset.title}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = selectedPreset.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = promptDraft,
                        onValueChange = { promptDraft = it },
                        label = { Text("Starting System Prompt (editable)") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 210.dp)
                            .testTag("session_special_prompt_input")
                    )

                    if (hasActiveProject && !activeProjectName.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Attach project '$activeProjectName'",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = attachProjectInSpecial,
                                onCheckedChange = { attachProjectInSpecial = it }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedMode == SessionMode.NORMAL) {
                        onStartSession(
                            SessionMode.NORMAL,
                            "normal",
                            "Normal Q&A",
                            SystemPromptPresets.NORMAL_QA_PROMPT,
                            attachProjectInNormal
                        )
                    } else {
                        onStartSession(
                            SessionMode.SPECIAL,
                            selectedPreset.id,
                            selectedPreset.title,
                            promptDraft,
                            attachProjectInSpecial
                        )
                    }
                },
                enabled = selectedMode == SessionMode.NORMAL || promptDraft.isNotBlank(),
                modifier = Modifier.testTag("confirm_new_session_button")
            ) {
                Icon(Icons.Rounded.AddComment, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (selectedMode == SessionMode.NORMAL) "Start Normal" else "Start Special")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
