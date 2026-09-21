package dev.rfnotebook.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.rfnotebook.maps.FieldMapModel
import dev.rfnotebook.maps.FieldMapView
import dev.rfnotebook.maps.FieldOfflineRegionManager
import dev.rfnotebook.maps.GeographicAggregator
import dev.rfnotebook.maps.MapDataset
import dev.rfnotebook.maps.MapFilters
import dev.rfnotebook.maps.OfflineBounds
import dev.rfnotebook.maps.OfflineRegionPhase
import dev.rfnotebook.maps.OfflineRegionProgress
import dev.rfnotebook.maps.OfflineRegionRequest
import dev.rfnotebook.maps.RenderedFingerprintLayer
import kotlin.math.max

private enum class GeographicViewMode { MAP, LIST }

@Composable
fun MapExplorerPage(
    dataset: MapDataset?,
    initialFingerprintId: String?,
    onBack: () -> Unit,
) {
    if (dataset == null) {
        Card(Modifier.fillMaxWidth()) { Text("Loading local geographic evidence…", Modifier.padding(16.dp)) }
        return
    }
    val availableLayers = dataset.layers
    var selectedIds by remember(initialFingerprintId, availableLayers) {
        mutableStateOf(setOfNotNull(initialFingerprintId ?: availableLayers.firstOrNull()?.fingerprintId))
    }
    var viewMode by remember { mutableStateOf(GeographicViewMode.MAP) }
    var selectedSurveys by remember { mutableStateOf(emptySet<String>()) }
    var selectedEquipment by remember { mutableStateOf(emptySet<String>()) }
    var selectedTypes by remember { mutableStateOf(emptySet<String>()) }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var minimumFrequencyMhz by remember { mutableStateOf("") }
    var maximumFrequencyMhz by remember { mutableStateOf("") }
    var minimumBandwidthKhz by remember { mutableStateOf("") }
    var maximumBandwidthKhz by remember { mutableStateOf("") }
    var minimumConfidence by remember { mutableStateOf(0f) }
    var filtersExpanded by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(14.0) }
    val filters = MapFilters(
        surveyIds = selectedSurveys,
        startEpochMs = startTime.toLongOrNull(),
        endEpochMs = endTime.toLongOrNull(),
        minimumFrequencyHz = minimumFrequencyMhz.toDoubleOrNull()?.times(1_000_000)?.toLong(),
        maximumFrequencyHz = maximumFrequencyMhz.toDoubleOrNull()?.times(1_000_000)?.toLong(),
        minimumBandwidthHz = minimumBandwidthKhz.toDoubleOrNull()?.times(1_000)?.toLong(),
        maximumBandwidthHz = maximumBandwidthKhz.toDoubleOrNull()?.times(1_000)?.toLong(),
        detectionTypes = selectedTypes,
        equipmentProfileVersionIds = selectedEquipment,
        minimumDetectionConfidence = minimumConfidence,
    )
    val palette = listOf("#D32F2F", "#1565C0", "#2E7D32", "#6A1B9A")
    val rendered = remember(availableLayers, selectedIds, filters, zoom) {
        availableLayers.filter { it.fingerprintId in selectedIds }.take(4).mapIndexed { index, layer ->
            RenderedFingerprintLayer(
                layer.fingerprintId,
                layer.label,
                palette[index],
                GeographicAggregator.prepare(
                    layer.observations,
                    filters,
                    layer.comparableEquipmentProfileVersionId,
                    includeIncompatibleEquipment = false,
                    zoom = zoom,
                ),
            )
        }
    }
    val filteredSurveyIds = if (selectedSurveys.isEmpty()) dataset.surveys.map { it.id }.toSet() else selectedSurveys
    val routes = dataset.routes.filter { it.surveyId in filteredSurveyIds }
    val gaps = dataset.gaps.filter { it.surveyId in filteredSurveyIds }
    val incompatibleComparison = rendered.mapNotNull { layer ->
        availableLayers.firstOrNull { it.fingerprintId == layer.fingerprintId }?.comparableEquipmentProfileVersionId
    }.distinct().size > 1
    val noData = rendered.all { it.aggregation.cells.isEmpty() }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Observed relative strength", style = MaterialTheme.typography.titleLarge)
        Text("These cells show where this app observed relative RF energy. They do not estimate or identify a transmitter location.")
        Text("Aggregation ${dev.rfnotebook.maps.MAP_AGGREGATION_VERSION} • relative dBFS evidence, not calibrated field strength")

        Text("Fingerprint layers", style = MaterialTheme.typography.titleMedium)
        availableLayers.forEach { layer ->
            val selected = layer.fingerprintId in selectedIds
            OutlinedButton(
                onClick = {
                    selectedIds = if (selected) {
                        if (selectedIds.size == 1) selectedIds else selectedIds - layer.fingerprintId
                    } else if (selectedIds.size < 4) selectedIds + layer.fingerprintId else selectedIds
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("${if (selected) "Showing" else "Compare"}: ${layer.label} • ${layer.comparableEquipmentProfileVersionId}")
            }
        }
        if (selectedIds.size > 1) Text("Comparison mode is explicit. Each fingerprint keeps a stable labeled color and separate equipment context.")
        if (incompatibleComparison) Text(
            "Comparable-equipment warning: selected layers use different equipment or gain versions. They remain separate and must not be compared as absolute strength.",
            color = MaterialTheme.colorScheme.error,
        )
        rendered.mapNotNull { it.aggregation.comparabilityWarning }.forEach { Text(it, color = MaterialTheme.colorScheme.error) }

        OutlinedButton(onClick = { filtersExpanded = !filtersExpanded }, modifier = Modifier.fillMaxWidth()) {
            Text(if (filtersExpanded) "Hide filters" else "Show filters")
        }
        if (filtersExpanded) FilterControls(
                dataset = dataset,
                selectedSurveys = selectedSurveys,
                onSurveys = { selectedSurveys = it },
                selectedEquipment = selectedEquipment,
                onEquipment = { selectedEquipment = it },
                selectedTypes = selectedTypes,
                onTypes = { selectedTypes = it },
                startTime = startTime, onStartTime = { startTime = it },
                endTime = endTime, onEndTime = { endTime = it },
                minimumFrequencyMhz = minimumFrequencyMhz, onMinimumFrequency = { minimumFrequencyMhz = it },
                maximumFrequencyMhz = maximumFrequencyMhz, onMaximumFrequency = { maximumFrequencyMhz = it },
                minimumBandwidthKhz = minimumBandwidthKhz, onMinimumBandwidth = { minimumBandwidthKhz = it },
                maximumBandwidthKhz = maximumBandwidthKhz, onMaximumBandwidth = { maximumBandwidthKhz = it },
                minimumConfidence = minimumConfidence, onConfidence = { minimumConfidence = it },
            )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { viewMode = GeographicViewMode.MAP }, modifier = Modifier.fillMaxWidth()) { Text("Map view") }
            Button(onClick = { viewMode = GeographicViewMode.LIST }, modifier = Modifier.fillMaxWidth()) { Text("Equivalent list") }
            OutlinedButton(onClick = { zoom = if (zoom >= 18.0) 11.0 else zoom + 1.0 }, modifier = Modifier.fillMaxWidth()) { Text("Cell zoom ${zoom.toInt()}") }
        }
        if (noData) Text("No comparable located data matches these filters. Missing-location observations remain counted below.")
        when (viewMode) {
            GeographicViewMode.MAP -> {
                AndroidView(
                    factory = { FieldMapView(it) },
                    update = { it.update(FieldMapModel(rendered, routes, gaps)) },
                    modifier = Modifier.fillMaxWidth().height(430.dp).semantics {
                        contentDescription = "Observed relative strength map with route, GPS accuracy, and acquisition gaps"
                    },
                )
                Text(FieldMapView.ATTRIBUTION)
                Legend(rendered)
            }
            GeographicViewMode.LIST -> EquivalentGeographicList(rendered, gaps)
        }
        EvidenceSummary(rendered, gaps)
        OfflineRegionControls(dataset, rendered)
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to signal detail") }
    }
}

@Composable
private fun FilterControls(
    dataset: MapDataset,
    selectedSurveys: Set<String>, onSurveys: (Set<String>) -> Unit,
    selectedEquipment: Set<String>, onEquipment: (Set<String>) -> Unit,
    selectedTypes: Set<String>, onTypes: (Set<String>) -> Unit,
    startTime: String, onStartTime: (String) -> Unit,
    endTime: String, onEndTime: (String) -> Unit,
    minimumFrequencyMhz: String, onMinimumFrequency: (String) -> Unit,
    maximumFrequencyMhz: String, onMaximumFrequency: (String) -> Unit,
    minimumBandwidthKhz: String, onMinimumBandwidth: (String) -> Unit,
    maximumBandwidthKhz: String, onMaximumBandwidth: (String) -> Unit,
    minimumConfidence: Float, onConfidence: (Float) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Filters", style = MaterialTheme.typography.titleMedium)
            Text("Survey")
            dataset.surveys.forEach { survey -> ToggleButton(
                "${survey.name} • ${survey.equipmentProfileVersionId}", survey.id in selectedSurveys,
            ) { onSurveys(toggle(selectedSurveys, survey.id)) } }
            Text("Equipment profile")
            dataset.surveys.map { it.equipmentProfileVersionId }.distinct().forEach { equipment ->
                ToggleButton(equipment, equipment in selectedEquipment) { onEquipment(toggle(selectedEquipment, equipment)) }
            }
            Text("Detection type")
            dataset.layers.flatMap { it.observations }.map { it.detectionType }.distinct().forEach { type ->
                ToggleButton(type.lowercase().replace('_', ' '), type in selectedTypes) { onTypes(toggle(selectedTypes, type)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(startTime, onStartTime, label = { Text("Start epoch ms") }, modifier = Modifier.weight(1f))
                OutlinedTextField(endTime, onEndTime, label = { Text("End epoch ms") }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(minimumFrequencyMhz, onMinimumFrequency, label = { Text("Min MHz") }, modifier = Modifier.weight(1f))
                OutlinedTextField(maximumFrequencyMhz, onMaximumFrequency, label = { Text("Max MHz") }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(minimumBandwidthKhz, onMinimumBandwidth, label = { Text("Min bandwidth kHz") }, modifier = Modifier.weight(1f))
                OutlinedTextField(maximumBandwidthKhz, onMaximumBandwidth, label = { Text("Max bandwidth kHz") }, modifier = Modifier.weight(1f))
            }
            Text("Minimum evidence confidence")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0f to "Any", 0.5f to "50%", 0.8f to "80%").forEach { (value, label) ->
                    ToggleButton(label, minimumConfidence == value) { onConfidence(value) }
                }
            }
        }
    }
}

@Composable private fun ToggleButton(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("${if (selected) "Selected" else "Include"}: $label") }
}

private fun toggle(values: Set<String>, value: String): Set<String> = if (value in values) values - value else values + value

@Composable private fun Legend(layers: List<RenderedFingerprintLayer>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Legend", style = MaterialTheme.typography.titleMedium)
            layers.forEach { Text("${it.label}: ${it.color}; stronger median relative dBFS is more opaque") }
            Text("Blue halos: reported GPS accuracy • dark route: recorded fixes • red dashed route: acquisition/location gap")
            Text("Cell confidence uses labeled support: low <3 samples; medium ≥3; high ≥5 across ≥2 surveys.")
        }
    }
}

@Composable private fun EquivalentGeographicList(layers: List<RenderedFingerprintLayer>, gaps: List<dev.rfnotebook.maps.MapGapSegment>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Equivalent geographic results", style = MaterialTheme.typography.titleMedium)
        layers.forEach { layer ->
            Text(layer.label, style = MaterialTheme.typography.titleSmall)
            layer.aggregation.cells.forEachIndexed { index, cell ->
                Card(Modifier.fillMaxWidth().semantics {
                    contentDescription = "Area ${index + 1}, ${cell.medianRelativeDb} relative dBFS, ${cell.sampleCount} observations, ${cell.confidence.name.lowercase()} confidence"
                }) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Area ${index + 1}: approximately ${cell.cellSizeM.toInt()} m grid cell")
                        Text("Median ${"%.1f".format(cell.medianRelativeDb)} relative dBFS • upper quartile ${"%.1f".format(cell.upperQuartileRelativeDb)} • spread ${"%.1f".format(cell.spreadDb)} dB")
                        Text("Support ${cell.sampleCount} observations / ${cell.surveyCount} surveys • ${cell.confidence.name.lowercase()} confidence")
                        Text("GPS uncertainty up to ${cell.maximumAccuracyM.toInt()} m • ${cell.interpolatedSampleCount} interpolated")
                        Text("Time ${cell.firstSeenEpochMs}…${cell.lastSeenEpochMs} • equipment ${cell.equipmentProfileVersionIds.joinToString()}")
                    }
                }
            }
        }
        if (gaps.isNotEmpty()) {
            Text("Acquisition and location gaps", style = MaterialTheme.typography.titleSmall)
            gaps.forEach { Text("${it.surveyId}: ${it.reason.lowercase().replace('_', ' ')} at ${it.startedEpochMs}${it.endedEpochMs?.let { end -> "…$end" } ?: " (open)"}; ${it.droppedUnitCount} counted dropped units. ${it.explanation}") }
        }
    }
}

@Composable private fun EvidenceSummary(layers: List<RenderedFingerprintLayer>, gaps: List<dev.rfnotebook.maps.MapGapSegment>) {
    val missing = layers.sumOf { it.aggregation.missingLocationCount }
    val interpolated = layers.sumOf { it.aggregation.interpolatedLocationCount }
    val stale = layers.sumOf { it.aggregation.staleLocationCount }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text("Uncertainty and coverage", style = MaterialTheme.typography.titleMedium)
            Text("$missing observations have no location; $stale have stale location; neither is placed on the map.")
            Text("$interpolated observations use explicitly marked interpolated fixes.")
            Text("${gaps.size} acquisition/location gaps remain visible; none were silently bridged.")
        }
    }
}

@Composable private fun OfflineRegionControls(dataset: MapDataset, layers: List<RenderedFingerprintLayer>) {
    val context = LocalContext.current
    val manager = remember { FieldOfflineRegionManager(context) }
    var regions by remember { mutableStateOf(emptyList<OfflineRegionProgress>()) }
    var message by remember { mutableStateOf("Checking saved offline regions…") }
    var confirmRemoval by remember { mutableStateOf<Long?>(null) }
    var minimumZoom by remember { mutableStateOf(11) }
    var maximumZoom by remember { mutableStateOf(16) }
    val bounds = remember(dataset, layers) { dataset.offlineBounds() }
    fun refresh() = manager.list {
        regions = it
        message = if (it.isEmpty()) "No saved offline regions." else "${it.size} saved offline region(s)."
    }
    LaunchedEffect(Unit) { refresh() }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Offline map region", style = MaterialTheme.typography.titleMedium)
            Text("Map tiles are separate from private survey evidence. The provider sees requested basemap tile addresses, but cannot read the notebook database, exact stored fixes, frequencies, notes, or signal metadata.")
            Text("Selected region: bounded observed route area")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { minimumZoom = if (minimumZoom >= maximumZoom) 8 else minimumZoom + 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text("Minimum zoom $minimumZoom")
                }
                OutlinedButton(onClick = { maximumZoom = if (maximumZoom >= 18) minimumZoom else maximumZoom + 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text("Maximum zoom $maximumZoom")
                }
            }
            Text(message)
            Button(enabled = bounds != null, onClick = {
                val request = OfflineRegionRequest("Observed area", requireNotNull(bounds), minimumZoom.toDouble(), maximumZoom.toDouble())
                manager.download(request) { progress ->
                    message = progress.explanation
                    if (progress.phase == OfflineRegionPhase.COMPLETE || progress.phase == OfflineRegionPhase.FAILED) refresh()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Download selected offline region") }
            regions.forEach { region ->
                Text("${region.request.name}: ${region.explanation}")
                if (region.phase == OfflineRegionPhase.INCOMPLETE || region.phase == OfflineRegionPhase.FAILED) {
                    OutlinedButton(onClick = { manager.resume(region.id) { message = it.explanation; if (it.phase == OfflineRegionPhase.COMPLETE) refresh() } }) { Text("Resume offline download") }
                }
                if (confirmRemoval == region.id) {
                    Text("Remove this region and its cached resources from this device?")
                    Button(onClick = { manager.remove(region.id) { message = it.explanation; confirmRemoval = null; refresh() } }) { Text("Confirm remove offline region") }
                } else OutlinedButton(onClick = { confirmRemoval = region.id }) { Text("Remove offline region") }
            }
            Text(FieldMapView.ATTRIBUTION)
        }
    }
}

private fun MapDataset.offlineBounds(): OfflineBounds? {
    val latitudes = routes.map { it.latitude } + layers.flatMap { it.observations.mapNotNull { observation -> observation.latitude } }
    val longitudes = routes.map { it.longitude } + layers.flatMap { it.observations.mapNotNull { observation -> observation.longitude } }
    if (latitudes.isEmpty() || longitudes.isEmpty()) return null
    val padding = 0.005
    val north = (latitudes.maxOrNull()!! + padding).coerceAtMost(85.0)
    val south = (latitudes.minOrNull()!! - padding).coerceAtLeast(-85.0)
    val east = (longitudes.maxOrNull()!! + padding).coerceAtMost(180.0)
    val west = (longitudes.minOrNull()!! - padding).coerceAtLeast(-180.0)
    return OfflineBounds(max(north, south + 0.0001), max(east, west + 0.0001), south, west)
}
