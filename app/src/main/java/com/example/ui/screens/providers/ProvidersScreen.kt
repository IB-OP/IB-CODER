package com.example.ui.screens.providers

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ToolSupportStatus
import com.example.ui.theme.JetBrainsMonoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(
    viewModel: ProvidersViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Providers", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Keys stored on-device with Android Keystore",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("providers_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 860.dp)
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("provider_search_input"),
                    placeholder = { Text("Search 23 providers or models...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.filter == ProviderFilter.ALL,
                        onClick = { viewModel.setFilter(ProviderFilter.ALL) },
                        label = { Text("All (${state.cards.size})") },
                        modifier = Modifier.testTag("filter_all")
                    )
                    FilterChip(
                        selected = state.filter == ProviderFilter.FREE_TIER,
                        onClick = { viewModel.setFilter(ProviderFilter.FREE_TIER) },
                        label = { Text("Free Tier") },
                        modifier = Modifier.testTag("filter_free")
                    )
                    FilterChip(
                        selected = state.filter == ProviderFilter.PAID_LOCAL,
                        onClick = { viewModel.setFilter(ProviderFilter.PAID_LOCAL) },
                        label = { Text("Paid & Local") },
                        modifier = Modifier.testTag("filter_paid")
                    )
                    FilterChip(
                        selected = state.filter == ProviderFilter.ENABLED,
                        onClick = { viewModel.setFilter(ProviderFilter.ENABLED) },
                        label = { Text("Enabled") },
                        modifier = Modifier.testTag("filter_enabled")
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.cards, key = { it.metadata.id }) { card ->
                        ProviderCard(
                            card = card,
                            expanded = state.expandedProviderId == card.metadata.id,
                            onToggleExpanded = { viewModel.toggleExpanded(card.metadata.id) },
                            onToggleEnabled = { enabled ->
                                viewModel.updateConfig(card.metadata.id) { it.copy(enabled = enabled) }
                            },
                            onApiKeyChanged = { viewModel.updateApiKey(card.metadata.id, it) },
                            onBaseUrlChanged = { url ->
                                viewModel.updateConfig(card.metadata.id) { it.copy(baseUrl = url) }
                            },
                            onAccountIdChanged = { acct ->
                                viewModel.updateConfig(card.metadata.id) { it.copy(accountId = acct) }
                            },
                            onDeploymentChanged = { dep ->
                                viewModel.updateConfig(card.metadata.id) {
                                    it.copy(deploymentName = dep, selectedModel = dep)
                                }
                            },
                            onModelSelected = { modelId ->
                                viewModel.updateConfig(card.metadata.id) { it.copy(selectedModel = modelId) }
                            },
                            onFetchModels = { viewModel.fetchModels(card.metadata.id) },
                            onToggleManualMode = { viewModel.toggleManualModelEntry(card.metadata.id) },
                            onTestConnection = { viewModel.testConnection(card.metadata.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(
    card: ProviderCardUiState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onAccountIdChanged: (String) -> Unit,
    onDeploymentChanged: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onFetchModels: () -> Unit,
    onToggleManualMode: () -> Unit,
    onTestConnection: () -> Unit
) {
    val context = LocalContext.current
    var showApiKey by remember { mutableStateOf(false) }
    var modelDropdownOpen by remember { mutableStateOf(false) }
    var modelSearch by remember { mutableStateOf("") }

    val borderColor = if (card.config.enabled) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_card_${card.metadata.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Card Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = card.metadata.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        CategoryBadge(card.metadata.category)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = card.metadata.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (card.config.selectedModel.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Model: ${card.config.selectedModel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = card.config.enabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.testTag("provider_enable_${card.metadata.id}")
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            // Tool-calling unsupported flag
            if (card.toolSupportStatus == ToolSupportStatus.UNSUPPORTED) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 10.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Selected model/provider does not support workspace tool calling (read/write/edit files).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    card.metadata.signupUrl?.let { url ->
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Get API Key: $url", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    // API Key Field
                    OutlinedTextField(
                        value = card.apiKey,
                        onValueChange = onApiKeyChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("provider_apikey_${card.metadata.id}"),
                        label = {
                            Text(
                                if (card.metadata.requiresApiKey) "API Key"
                                else "API Key (Optional)"
                            )
                        },
                        placeholder = { Text("Paste key (stored encrypted on-device)") },
                        leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                        trailingIcon = {
                            IconButton(
                                onClick = { showApiKey = !showApiKey },
                                modifier = Modifier.testTag("provider_key_toggle_${card.metadata.id}")
                            ) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (showApiKey) "Hide API key" else "Show API key"
                                )
                            }
                        },
                        visualTransformation = if (showApiKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Base URL Field
                    OutlinedTextField(
                        value = card.config.baseUrl,
                        onValueChange = onBaseUrlChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("provider_baseurl_${card.metadata.id}"),
                        label = { Text("Base URL") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily)
                    )

                    // Extra Account ID for Cloudflare
                    if (card.metadata.requiresAccountId) {
                        OutlinedTextField(
                            value = card.config.accountId,
                            onValueChange = onAccountIdChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("provider_accountid_${card.metadata.id}"),
                            label = { Text("Cloudflare Account ID") },
                            placeholder = { Text("32-character Account ID") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Extra Deployment Name for Azure OpenAI
                    if (card.metadata.requiresDeploymentName) {
                        OutlinedTextField(
                            value = card.config.deploymentName,
                            onValueChange = onDeploymentChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("provider_deployment_${card.metadata.id}"),
                            label = { Text("Azure Deployment Name") },
                            placeholder = { Text("e.g. gpt-4o") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Model Picker Section (Live /models + Manual fallback)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Model Selection",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (card.metadata.supportsModelsEndpoint) {
                                    TextButton(
                                        onClick = onFetchModels,
                                        enabled = !card.isFetchingModels,
                                        modifier = Modifier.testTag("provider_fetch_models_${card.metadata.id}")
                                    ) {
                                        if (card.isFetchingModels) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(Modifier.width(6.dp))
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.CloudDownload,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        Text("Fetch /models")
                                    }
                                    TextButton(
                                        onClick = onToggleManualMode,
                                        modifier = Modifier.testTag("provider_manual_toggle_${card.metadata.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (card.isManualModelEntry) "Picker" else "Manual")
                                    }
                                }
                            }
                        }

                        if (!card.isManualModelEntry && card.models.isNotEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = card.config.selectedModel,
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("provider_model_picker_${card.metadata.id}"),
                                    label = { Text("Selected Model (${card.models.size} fetched)") },
                                    placeholder = { Text("Tap to choose a live model") },
                                    trailingIcon = {
                                        IconButton(onClick = { modelDropdownOpen = true }) {
                                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Choose model")
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable { modelDropdownOpen = true }
                                )
                                DropdownMenu(
                                    expanded = modelDropdownOpen,
                                    onDismissRequest = { modelDropdownOpen = false },
                                    modifier = Modifier
                                        .widthIn(min = 280.dp, max = 380.dp)
                                        .heightIn(max = 360.dp)
                                ) {
                                    OutlinedTextField(
                                        value = modelSearch,
                                        onValueChange = { modelSearch = it },
                                        placeholder = { Text("Filter models...") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                    val filtered = card.models.filter {
                                        modelSearch.isBlank() || it.id.contains(modelSearch, ignoreCase = true)
                                    }.take(60)
                                    filtered.forEach { model ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        text = model.id,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontFamily = JetBrainsMonoFamily
                                                    )
                                                    if (model.supportsToolCalling == ToolSupportStatus.UNSUPPORTED) {
                                                        Text(
                                                            text = "No tool calling",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                onModelSelected(model.id)
                                                modelDropdownOpen = false
                                                modelSearch = ""
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = card.config.selectedModel,
                                onValueChange = onModelSelected,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("provider_model_manual_${card.metadata.id}"),
                                label = { Text("Model ID") },
                                placeholder = { Text(card.metadata.modelPlaceholder) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily)
                            )
                        }

                        card.modelsError?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Test Connection button & Result banner
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onTestConnection,
                            enabled = !card.isTesting,
                            modifier = Modifier.testTag("provider_test_${card.metadata.id}"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (card.isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Testing...")
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.NetworkCheck,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Test Connection")
                            }
                        }

                        if (!card.config.enabled) {
                            Button(
                                onClick = { onToggleEnabled(true) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Enable")
                            }
                        }
                    }

                    card.testResult?.let { result ->
                        Surface(
                            color = if (result.success) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (result.success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (result.success) {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    }
                                )
                                Text(
                                    text = result.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (result.success) {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBadge(category: ProviderCategory) {
    val (label, container, content) = when (category) {
        ProviderCategory.FREE_TIER -> Triple(
            "FREE TIER",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        ProviderCategory.PAID_POPULAR -> Triple(
            "POPULAR",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        ProviderCategory.LOCAL -> Triple(
            "LOCAL",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        ProviderCategory.CUSTOM -> Triple(
            "CUSTOM",
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurface
        )
    }
    Surface(color = container, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
