package co.sirdab.driver.shared.feature.onboarding.impl.presentation.signup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.analytics.Analytics
import co.sirdab.driver.shared.core.analytics.AnalyticsEvent
import co.sirdab.driver.shared.core.analytics.track
import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.platform.locale.AppLocale
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.DriverTextField
import co.sirdab.driver.shared.core.ui.components.ErrorText
import co.sirdab.driver.shared.core.ui.components.OptionRow
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.routes_add
import co.sirdab.driver.shared.core.ui.generated.resources.routes_from
import co.sirdab.driver.shared.core.ui.generated.resources.routes_full
import co.sirdab.driver.shared.core.ui.generated.resources.routes_home_city
import co.sirdab.driver.shared.core.ui.generated.resources.routes_no_match
import co.sirdab.driver.shared.core.ui.generated.resources.routes_preferred
import co.sirdab.driver.shared.core.ui.generated.resources.routes_preferred_hint
import co.sirdab.driver.shared.core.ui.generated.resources.routes_save
import co.sirdab.driver.shared.core.ui.generated.resources.routes_search
import co.sirdab.driver.shared.core.ui.generated.resources.routes_subtitle
import co.sirdab.driver.shared.core.ui.generated.resources.routes_title
import co.sirdab.driver.shared.core.ui.generated.resources.routes_to
import co.sirdab.driver.shared.core.ui.generated.resources.signup_remove_truck
import co.sirdab.driver.shared.core.ui.generated.resources.trips_retry
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.feature.onboarding.api.domain.City
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverOnboardingRepository
import co.sirdab.driver.shared.feature.onboarding.api.domain.PreferredRoute
import co.sirdab.driver.shared.feature.onboarding.api.domain.RoutesDraft
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Which field the city sheet is filling in. */
enum class CityField { HOME, ORIGIN, DESTINATION }

data class RoutesUiState(
    val draft: RoutesDraft = RoutesDraft(),
    /** The two ends of a route being put together, before it joins the list. */
    val origin: City? = null,
    val destination: City? = null,
    /** The field the city sheet is open for, or null while it is closed. */
    val picking: CityField? = null,
    val query: String = "",
    /** What the search found. Null while the sheet's first read is out, or after one failed. */
    val cities: List<City>? = null,
    val citiesError: AppError? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val errorReason: AppErrorReason? = null,
) {
    val canAddRoute: Boolean get() = origin != null && destination != null && !draft.isFull
}

/**
 * Where the driver lives and the routes they would rather take: one PATCH to their own profile.
 *
 * Opened from sign-up and from the profile tab alike, and seeded from the profile either way, so a
 * driver adding a route later starts from the ones they already have.
 *
 * Nothing is written until the driver saves. The PATCH replaces the whole list, so adding and
 * removing routes here and sending once is the same as sending each change, and cheaper.
 */
class RoutesViewModel(
    private val repository: DriverOnboardingRepository,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(
        RoutesUiState(
            draft = repository.profile.value?.let {
                RoutesDraft(homeCity = it.homeCity, routes = it.preferredRoutes)
            } ?: RoutesDraft(),
        ),
    )
    val state: StateFlow<RoutesUiState> = _state.asStateFlow()

    private var search: Job? = null

    /**
     * The answer to a blank search, kept for the next time the sheet opens. It is the list the sheet
     * opens on, three times for one route, and it does not change while the screen is open.
     */
    private var biggest: List<City>? = null

    fun openPicker(field: CityField) {
        _state.update { it.copy(picking = field, query = "", cities = biggest, citiesError = null) }
        if (biggest == null) search(query = "", wait = false)
    }

    fun closePicker() {
        search?.cancel()
        _state.update { it.copy(picking = null) }
    }

    fun onQuery(query: String) {
        _state.update { it.copy(query = query) }
        if (query.isBlank() && biggest != null) {
            search?.cancel()
            _state.update { it.copy(cities = biggest, citiesError = null) }
        } else {
            search(query, wait = true)
        }
    }

    fun retrySearch() = search(_state.value.query, wait = false)

    /**
     * [wait] while the driver is typing: a request per letter would answer mostly for words they
     * had not finished, and on a weak signal those arrive after the one they meant.
     */
    private fun search(query: String, wait: Boolean) {
        search?.cancel()
        search = viewModelScope.launch {
            if (wait) delay(SEARCH_DELAY_MILLIS)
            _state.update { it.copy(citiesError = null) }
            when (val result = repository.searchCities(query)) {
                is AppResult.Success -> {
                    if (query.isBlank()) biggest = result.data
                    _state.update { it.copy(cities = result.data) }
                }
                // The list on screen answers a different search, so it goes: the error and a retry
                // are more use than cities that do not match what was typed.
                is AppResult.Failure -> _state.update { it.copy(cities = null, citiesError = result.error) }
            }
        }
    }

    fun pick(city: City) {
        search?.cancel()
        _state.update { state ->
            when (state.picking) {
                CityField.HOME -> state.copy(draft = state.draft.copy(homeCity = city))
                CityField.ORIGIN -> state.copy(origin = city)
                CityField.DESTINATION -> state.copy(destination = city)
                null -> state
            }.copy(picking = null, errorMessage = null, errorReason = null)
        }
    }

    fun addRoute() {
        val state = _state.value
        val origin = state.origin ?: return
        val destination = state.destination ?: return
        _state.value = state.copy(
            draft = state.draft.withRoute(PreferredRoute(origin = origin, destination = destination)),
            origin = null,
            destination = null,
            errorMessage = null,
            errorReason = null,
        )
    }

    fun removeRoute(route: PreferredRoute) {
        _state.update {
            it.copy(
                draft = it.draft.copy(routes = it.draft.routes - route),
                errorMessage = null,
                errorReason = null,
            )
        }
    }

    fun save(onSaved: () -> Unit) {
        val draft = _state.value.draft
        if (!draft.canSubmit || _state.value.isSubmitting) return

        _state.update { it.copy(isSubmitting = true, errorMessage = null, errorReason = null) }
        viewModelScope.launch {
            when (val result = repository.saveRoutes(draft)) {
                is AppResult.Success -> {
                    analytics.track(AnalyticsEvent.routesSaved(draft.routes.size))
                    _state.update { it.copy(isSubmitting = false) }
                    onSaved()
                }
                is AppResult.Failure -> {
                    analytics.track(AnalyticsEvent.routesSaveFailed(result.error))
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = result.error.message,
                            errorReason = result.error.reason,
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val SEARCH_DELAY_MILLIS = 300L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesScreen(
    onSaved: () -> Unit,
    viewModel: RoutesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val draft = state.draft
    val lang = AppLocale.current()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
        ) {
            Text(stringResource(Res.string.routes_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(Res.string.routes_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.lg))
            CityPickerField(
                label = stringResource(Res.string.routes_home_city),
                city = draft.homeCity,
                lang = lang,
                onClick = { viewModel.openPicker(CityField.HOME) },
            )

            Spacer(Modifier.height(Spacing.xl))
            Text(
                stringResource(Res.string.routes_preferred),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                stringResource(Res.string.routes_preferred_hint, RoutesDraft.MAX_PREFERRED_ROUTES),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (draft.routes.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    draft.routes.forEach { route ->
                        RouteRow(route = route, lang = lang, onRemove = { viewModel.removeRoute(route) })
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))
            if (draft.isFull) {
                Text(
                    stringResource(Res.string.routes_full),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CityPickerField(
                        label = stringResource(Res.string.routes_from),
                        city = state.origin,
                        lang = lang,
                        onClick = { viewModel.openPicker(CityField.ORIGIN) },
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CityPickerField(
                        label = stringResource(Res.string.routes_to),
                        city = state.destination,
                        lang = lang,
                        onClick = { viewModel.openPicker(CityField.DESTINATION) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                OutlinedButton(onClick = viewModel::addRoute, enabled = state.canAddRoute) {
                    Text(stringResource(Res.string.routes_add))
                }
            }

            if (state.errorReason != null || state.errorMessage != null) {
                Spacer(Modifier.height(Spacing.sm))
                ErrorText(state.errorReason, state.errorMessage)
            }

            Spacer(Modifier.height(Spacing.xl))
            DriverButton(
                text = stringResource(Res.string.routes_save),
                onClick = { viewModel.save(onSaved) },
                enabled = draft.canSubmit,
                isLoading = state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }

    val picking = state.picking
    if (picking != null) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closePicker,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            CitySearch(
                title = stringResource(
                    when (picking) {
                        CityField.HOME -> Res.string.routes_home_city
                        CityField.ORIGIN -> Res.string.routes_from
                        CityField.DESTINATION -> Res.string.routes_to
                    },
                ),
                state = state,
                selected = when (picking) {
                    CityField.HOME -> draft.homeCity
                    CityField.ORIGIN -> state.origin
                    CityField.DESTINATION -> state.destination
                },
                lang = lang,
                onQuery = viewModel::onQuery,
                onPick = viewModel::pick,
                onRetry = viewModel::retrySearch,
            )
        }
    }
}

/**
 * The cities, searchable by either name. Opens on the biggest, because that is where most loads
 * start and end, and most drivers will find theirs without typing.
 */
@Composable
private fun CitySearch(
    title: String,
    state: RoutesUiState,
    selected: City?,
    lang: String,
    onQuery: (String) -> Unit,
    onPick: (City) -> Unit,
    onRetry: () -> Unit,
) {
    val cities = state.cities
    Column(Modifier.padding(horizontal = Spacing.lg)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Spacing.sm))
        DriverTextField(
            value = state.query,
            onValueChange = onQuery,
            label = stringResource(Res.string.routes_search),
        )
        Spacer(Modifier.height(Spacing.sm))
        when {
            cities == null && state.citiesError != null -> Column {
                ErrorText(state.citiesError)
                Spacer(Modifier.height(Spacing.xs))
                OutlinedButton(onClick = onRetry) {
                    Text(stringResource(Res.string.trips_retry))
                }
            }

            cities == null -> Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            cities.isEmpty() -> Text(
                stringResource(Res.string.routes_no_match),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.md),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                items(cities, key = { it.id }) { city ->
                    OptionRow(
                        label = city.displayName(lang),
                        selected = city.id == selected?.id,
                        onClick = { onPick(city) },
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.lg))
    }
}

/** A city in the shape of a text field, which opens the search rather than taking typing. */
@Composable
private fun CityPickerField(
    label: String,
    city: City?,
    lang: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = city?.displayName(lang).orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(Radius.md),
            modifier = Modifier.fillMaxWidth(),
        )
        // The field swallows taps, readOnly or not, so the whole of it is covered by something that
        // does open the search.
        Box(
            Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        )
    }
}

@Composable
private fun RouteRow(route: PreferredRoute, lang: String, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = Spacing.md, end = Spacing.xs).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Two texts and an arrow rather than one string with "→" in it: the arrow has to turn
            // round in Arabic and Urdu, and a character in the middle of a sentence does not.
            Text(
                route.origin.displayName(lang),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                route.destination.displayName(lang),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRemove) {
                Text(stringResource(Res.string.signup_remove_truck), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
