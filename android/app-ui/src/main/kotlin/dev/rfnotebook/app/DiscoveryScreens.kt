package dev.rfnotebook.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rfnotebook.domain.FingerprintState
import dev.rfnotebook.storage.DiscoveryDetail
import dev.rfnotebook.storage.SignalFingerprintEntity

@Composable
fun DiscoveriesPage(
    state: DiscoveryUiState,
    onRefresh: () -> Unit,
    onSelect: (SignalFingerprintEntity) -> Unit,
    onBack: () -> Unit,
    onProcessSurvey: (String) -> Unit = {},
    onMerge: (Set<String>) -> Unit = {},
) {
    var minimumMhz by remember { mutableStateOf("") }
    var maximumMhz by remember { mutableStateOf("") }
    var fromEpochMs by remember { mutableStateOf("") }
    var toEpochMs by remember { mutableStateOf("") }
    var selectedState by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf("") }
    var survey by remember { mutableStateOf("") }
    var equipment by remember { mutableStateOf("") }
    var selectedForMerge by remember { mutableStateOf(emptySet<String>()) }
    val filters = DiscoveryFilters(
        minimumFrequencyHz = minimumMhz.toDoubleOrNull()?.times(1_000_000)?.toLong(),
        maximumFrequencyHz = maximumMhz.toDoubleOrNull()?.times(1_000_000)?.toLong(),
        fromEpochMs = fromEpochMs.toLongOrNull(),
        toEpochMs = toEpochMs.toLongOrNull(),
        state = selectedState,
        hint = hint.trim().uppercase().replace(' ', '_').takeIf(String::isNotBlank),
        surveyId = survey.trim().takeIf(String::isNotBlank),
        equipmentProfileVersionId = equipment.trim().takeIf(String::isNotBlank),
    )
    val visible = if (state.details.isEmpty()) state.fingerprints else DiscoveryPresentation.filterAndRank(state.details, filters)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Discoveries", style = MaterialTheme.typography.titleLarge)
            Text("Ranked by novelty, relative strength, recurrence, and available location evidence.")
            Text("Filters")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(minimumMhz, { minimumMhz = it }, label = { Text("Min MHz") }, modifier = Modifier.weight(1f))
                OutlinedTextField(maximumMhz, { maximumMhz = it }, label = { Text("Max MHz") }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(fromEpochMs, { fromEpochMs = it }, label = { Text("From epoch ms") }, modifier = Modifier.weight(1f))
                OutlinedTextField(toEpochMs, { toEpochMs = it }, label = { Text("To epoch ms") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(hint, { hint = it }, label = { Text("Hint category") }, modifier = Modifier.fillMaxWidth())
            Text("Hint examples: continuous narrowband carrier, repeating short OOK-like burst, two-level FSK-like, analog FM-like, or likely local interference/overload.")
            OutlinedTextField(survey, { survey = it }, label = { Text("Survey ID") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(equipment, { equipment = it }, label = { Text("Comparable equipment version") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf<String?>(null, "NEW", "INTERESTING").forEach { value ->
                    OutlinedButton(onClick = { selectedState = value }) { Text((value ?: "ALL").lowercase()) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("IGNORED", "ARTIFACT").forEach { value ->
                    OutlinedButton(onClick = { selectedState = value }) { Text(value.lowercase()) }
                }
            }
            when (state.phase) {
                DiscoveryPhase.EMPTY -> Text("No discoveries yet. Complete a survey, then process its stored aggregates.")
                DiscoveryPhase.PROCESSING -> Text("Processing stored observations… The radio is not required.")
                DiscoveryPhase.FAILED -> Text("Reprocessing failed: ${state.problem ?: "unknown error"}", color = MaterialTheme.colorScheme.error)
                DiscoveryPhase.PARTIAL -> Text("Partial results are shown. ${state.problem.orEmpty()}", color = MaterialTheme.colorScheme.error)
                DiscoveryPhase.CONTENT -> Unit
            }
            if (state.reprocessableSurveys.isNotEmpty()) {
                Text("Offline reprocessing")
                state.reprocessableSurveys.forEach { completedSurvey ->
                    OutlinedButton(onClick = { onProcessSurvey(completedSurvey.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Process ${completedSurvey.name}")
                    }
                    OutlinedButton(onClick = { survey = completedSurvey.id }, modifier = Modifier.fillMaxWidth()) {
                        Text("Filter to ${completedSurvey.name}")
                    }
                }
            }
            if (state.phase == DiscoveryPhase.CONTENT && visible.isEmpty()) Text("No discoveries match these filters.")
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(visible, key = { it.id }) { fingerprint ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { onSelect(fingerprint) }, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text(fingerprint.userLabel.ifBlank { "${formatMhz(fingerprint.nominalFrequencyHz)} MHz discovery" })
                                Text("${fingerprint.state.lowercase()} • ${fingerprint.occurrenceCount} observations • ${fingerprint.typicalBandwidthHz / 1_000} kHz")
                                Text("Comparable equipment: ${fingerprint.equipmentProfileVersionId}")
                            }
                        }
                        OutlinedButton(onClick = {
                            selectedForMerge = if (fingerprint.id in selectedForMerge) {
                                selectedForMerge - fingerprint.id
                            } else selectedForMerge + fingerprint.id
                        }) { Text(if (fingerprint.id in selectedForMerge) "Selected for merge" else "Select for merge") }
                    }
                }
            }
            Button(enabled = selectedForMerge.size >= 2, onClick = { onMerge(selectedForMerge) }) {
                Text("Merge selected fingerprints")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack) { Text("Back") }
                Button(onClick = onRefresh) { Text("Refresh") }
            }
        }
    }
}

@Composable
fun DiscoveryDetailPage(
    detail: DiscoveryDetail?,
    onState: (FingerprintState) -> Unit,
    onSave: (String, Set<String>, String) -> Unit,
    onBack: () -> Unit,
    onMap: () -> Unit = {},
    onCapture: () -> Unit = {},
    onSplitLast: () -> Unit = {},
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Signal detail", style = MaterialTheme.typography.titleLarge)
            if (detail == null) Text("Loading discovery evidence…") else {
                val fingerprint = detail.fingerprint
                var label by remember(fingerprint.id, fingerprint.userLabel) { mutableStateOf(fingerprint.userLabel) }
                var tags by remember(fingerprint.id, fingerprint.tags) { mutableStateOf(fingerprint.tags.replace('|', ',')) }
                var notes by remember(fingerprint.id, fingerprint.notes) { mutableStateOf(fingerprint.notes) }
                Text("${formatMhz(fingerprint.nominalFrequencyHz)} MHz • typical bandwidth ${fingerprint.typicalBandwidthHz / 1_000} kHz")
                Text("Comparable equipment: ${fingerprint.equipmentProfileVersionId}")
                Text("First/last seen: ${fingerprint.firstSeenAtEpochMs} / ${fingerprint.lastSeenAtEpochMs}")
                Text("Occurrences: ${fingerprint.occurrenceCount} • duty cycle ${"%.1f".format(fingerprint.dutyCycleEstimate * 100)}%")
                if (fingerprint.locatedObservationCount > 0) {
                    Text("Location extent: ${fingerprint.minimumLatitude}…${fingerprint.maximumLatitude}, ${fingerprint.minimumLongitude}…${fingerprint.maximumLongitude} (${fingerprint.locatedObservationCount} located observations)")
                } else Text("Location extent: unavailable; observations remain reviewable without a fix.")
                Text("Typical duration: ${fingerprint.typicalBurstDurationMs?.let { "$it ms" } ?: "continuous/unknown"}; repeat: ${fingerprint.typicalRepeatIntervalMs?.let { "$it ms" } ?: "unknown"}")
                Text("Relative SNR timeline (not calibrated):")
                detail.detections.forEach { detection ->
                    Text("${detection.startedAtEpochMs}: ${"%.1f".format(detection.snrDb)} dB SNR • ${detection.kind.lowercase()}${if (detection.qualityFlags.isBlank()) "" else " • ${detection.qualityFlags}"}")
                }
                Text("Classification hints are cautious, not protocol identities:")
                if (detail.hints.isEmpty()) Text("No confident shape hint from aggregate evidence.")
                detail.hints.forEach { hint ->
                    Text("${hint.category.lowercase().replace('_', ' ')} — ${"%.0f".format(hint.confidence * 100)}%: ${hint.evidence}")
                }
                if (detail.provenance.isNotEmpty()) {
                    Text("Correction history:")
                    detail.provenance.forEach { Text("${it.operation}: ${it.explanation}") }
                }
                OutlinedTextField(label, { label = it }, label = { Text("User label") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tags, { tags = it }, label = { Text("Tags (comma separated)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    onSave(label, tags.split(',').map(String::trim).filter(String::isNotBlank).toSet(), notes)
                }) { Text("Save label, tags, and notes") }
                Button(onClick = onMap, modifier = Modifier.fillMaxWidth()) {
                    Text("Open observed relative strength map")
                }
                Button(onClick = onCapture, modifier = Modifier.fillMaxWidth()) {
                    Text("Revisit in focused RX and capture IQ")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { onState(FingerprintState.INTERESTING) }) { Text("Interesting") }
                    OutlinedButton(onClick = { onState(FingerprintState.IGNORED) }) { Text("Ignore") }
                    OutlinedButton(onClick = { onState(FingerprintState.ARTIFACT) }) { Text("Artifact") }
                }
                OutlinedButton(enabled = detail.detections.size > 1, onClick = onSplitLast) {
                    Text("Split last observation")
                }
            }
            OutlinedButton(onClick = onBack) { Text("Back to discoveries") }
        }
    }
}

private fun formatMhz(frequencyHz: Long): String = "%.4f".format(frequencyHz / 1_000_000.0)
