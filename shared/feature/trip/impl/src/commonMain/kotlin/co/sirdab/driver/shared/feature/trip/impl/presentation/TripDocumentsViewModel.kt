package co.sirdab.driver.shared.feature.trip.impl.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.analytics.Analytics
import co.sirdab.driver.shared.core.analytics.AnalyticsEvent
import co.sirdab.driver.shared.core.analytics.track
import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.platform.documents.DocumentViewer
import co.sirdab.driver.shared.feature.trip.api.DriverTripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One of a trip's documents: the leg whose order lists it, and its key there. */
data class DocumentRef(val legId: String, val key: String)

/** A document the driver asked for and did not get. A null [reason] is most often no signal. */
data class DocumentFailure(val document: DocumentRef, val reason: AppErrorReason?)

data class TripDocumentsUiState(
    /** On its way down. One at a time: each can be megabytes, over one bar of signal. */
    val downloading: DocumentRef? = null,
    /** The last one that failed, until the driver tries one again. */
    val failure: DocumentFailure? = null,
)

/**
 * Downloads the order documents a trip's legs list, and opens them in the phone's PDF viewer.
 *
 * Apart from the trip's own view model: a document is not trip work. Nothing is queued and nothing
 * is recorded against a stop; it is a read the driver repeats when it fails.
 */
class TripDocumentsViewModel(
    private val tripId: String,
    private val repository: DriverTripRepository,
    private val viewer: DocumentViewer,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(TripDocumentsUiState())
    val state: StateFlow<TripDocumentsUiState> = _state.asStateFlow()

    fun open(legId: String, key: String) {
        // A second tap while the first is still coming would only fetch it twice.
        if (_state.value.downloading != null) return
        val document = DocumentRef(legId, key)
        _state.value = TripDocumentsUiState(downloading = document)

        viewModelScope.launch {
            val error = when (val result = repository.legDocument(legId, key)) {
                is AppResult.Success -> {
                    val pdf = result.data
                    if (viewer.showPdf(pdf.bytes, pdf.fileName)) {
                        analytics.track(
                            AnalyticsEvent.orderDocumentOpened(tripId, legId, key, kilobytes = pdf.bytes.size / 1024),
                        )
                        null
                    } else {
                        AppError("Nothing on this phone can open a PDF.", reason = AppErrorReason.NO_DOCUMENT_VIEWER)
                    }
                }
                is AppResult.Failure -> result.error
            }
            if (error != null) analytics.track(AnalyticsEvent.orderDocumentFailed(tripId, legId, key, error))
            _state.value = TripDocumentsUiState(failure = error?.let { DocumentFailure(document, it.reason) })
        }
    }
}
