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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ProviderApiDiscoveryResult
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ToolSupportStatus
import com.example.ui.theme.JetBrainsMonoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(
    viewModel: ProvidersViewModel,
    onNavigateBack: (() -> Unit)? = null,
    onOpenSidebar: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddCustomDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "AI Providers",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${state.totalProviderCount} providers • Synced",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    if (onOpenSidebar != null) {
                        IconButton(
                            onClick = onOpenSidebar,
                            modifier = Modifier.testTag("providers_menu_button")
                        ) {
                            Icon(Icons.Rounded.Menu, contentDescription = "Open navigation sidebar")
                        }
                    } else if (onNavigateBack != null) {
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
                },
                actions = {
                    TextButton(
                        onClick = { showAddCustomDialog = true },
                        modifier = Modifier.testTag("providers_add_custom_top_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Custom")
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
                    placeholder = { Text("Search providers, URLs, or models...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
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
                        selected = state.filter == ProviderFilter.CUSTOM,
                        onClick = { viewModel.setFilter(ProviderFilter.CUSTOM) },
                        label = { Text("Custom") },
                        modifier = Modifier.testTag("filter_custom")
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
                    item(key = "add_custom_banner") {
                        Card(
                            onClick = { showAddCustomDialog = true },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("providers_add_custom_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Add Your Own Custom Provider",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Find any OpenAI-compatible API URL online, paste the link, and add your API key.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(onClick = { showAddCustomDialog = true }) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Create")
                                }
                            }
                        }
                    }

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
                            onTestConnection = { viewModel.testConnection(card.metadata.id) },
                            onDeleteCustom = { viewModel.deleteCustomProvider(card.metadata.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddCustomDialog) {
        AddCustomProviderDialog(
            initialSearchQuery = state.apiDiscoveryQuery,
            discoveryResults = state.apiDiscoveryResults,
            isSearching = state.isSearchingApiUrl,
            onSearchApiUrl = viewModel::findApiEndpoint,
            onAddProvider = { name, baseUrl, apiKey, defaultModel, docsUrl, requiresKey ->
                viewModel.addCustomProvider(
                    name = name,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    defaultModel = defaultModel,
                    docsUrl = docsUrl,
                    requiresApiKey = requiresKey
                )
                showAddCustomDialog = false
            },
            onDismiss = { showAddCustomDialog = false }
        )
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
    onTestConnection: () -> Unit,
    onDeleteCustom: () -> Unit
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
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Enable")
                            }
                        }

                        if (card.isUserCreatedCustom) {
                            IconButton(
                                onClick = onDeleteCustom,
                                modifier = Modifier.testTag("provider_delete_${card.metadata.id}")
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Delete custom provider",
                                    tint = MaterialTheme.colorScheme.error
                                )
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

@Composable
private fun AddCustomProviderDialog(
    initialSearchQuery: String,
    discoveryResults: List<ProviderApiDiscoveryResult>,
    isSearching: Boolean,
    onSearchApiUrl: (String) -> Unit,
    onAddProvider: (
        name: String,
        baseUrl: String,
        apiKey: String,
        defaultModel: String,
        docsUrl: String?,
        requiresApiKey: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchInput by remember { mutableStateOf(initialSearchQuery) }
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var docsUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var defaultModel by remember { mutableStateOf("") }
    var requiresKey by remember { mutableStateOf(true) }
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Add Custom Provider")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "1. Find the API URL you want to add (searches curated & live web endpoints):",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = { searchInput = it },
                        placeholder = { Text("e.g. cerebras, chutes, deepinfra") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_provider_find_input")
                    )
                    Button(
                        onClick = { onSearchApiUrl(searchInput) },
                        enabled = !isSearching,
                        modifier = Modifier.testTag("custom_provider_find_button")
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Find")
                        }
                    }
                }

                if (discoveryResults.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        discoveryResults.take(4).forEach { hit ->
                            Surface(
                                onClick = {
                                    if (name.isBlank()) name = hit.name
                                    baseUrl = hit.baseUrl
                                    docsUrl = hit.docsUrl.orEmpty()
                                },
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (baseUrl.equals(hit.baseUrl, ignoreCase = true)) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Link,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = hit.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = hit.baseUrl,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFamily,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    hit.docsUrl?.let { dUrl ->
                                        IconButton(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(dUrl)))
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Rounded.OpenInNew,
                                                contentDescription = "Open API docs",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = "2. Enter URL, Provider Name & API Key:",
                    style = MaterialTheme.typography.labelLarge
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Provider Name") },
                    placeholder = { Text("e.g. Cerebras AI") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_provider_name_input")
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API Base URL") },
                    placeholder = { Text("https://api.example.com/v1") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_provider_url_input")
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(if (requiresKey) "API Key" else "API Key (Optional)") },
                    placeholder = { Text("sk-... (stored encrypted)") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showKey) "Hide key" else "Show key"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_provider_key_input")
                )

                OutlinedTextField(
                    value = defaultModel,
                    onValueChange = { defaultModel = it },
                    label = { Text("Default Model ID (Optional — auto-fetches /models)") },
                    placeholder = { Text("e.g. llama-3.3-70b") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Requires API Key header", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = requiresKey, onCheckedChange = { requiresKey = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAddProvider(name, baseUrl, apiKey, defaultModel, docsUrl.ifBlank { null }, requiresKey)
                },
                enabled = baseUrl.isNotBlank() && (!requiresKey || apiKey.isNotBlank()),
                modifier = Modifier.testTag("custom_provider_save_button")
            ) {
                Text("Add Provider")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

