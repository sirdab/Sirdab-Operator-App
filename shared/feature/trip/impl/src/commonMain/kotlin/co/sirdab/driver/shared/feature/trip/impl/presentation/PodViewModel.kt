package co.sirdab.driver.shared.feature.trip.impl.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.model.ProofOfDelivery
import co.sirdab.driver.shared.feature.trip.api.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class PodUiState(
    val photoCount: Int = 0,
    val recipient: String = "",
    val seal: String = "",
    val hasSignature: Boolean = false,
    val clearKey: Int = 0,
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean get() = photoCount > 0 && recipient.isNotBlank() && hasSignature
}

@OptIn(ExperimentalTime::class)
class PodViewModel(
    private val tripId: String,
    private val tripRepository: TripRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PodUiState())
    val state: StateFlow<PodUiState> = _state.asStateFlow()

    fun addPhoto() { _state.value = _state.value.copy(photoCount = _state.value.photoCount + 1) }
    fun onRecipient(v: String) { _state.value = _state.value.copy(recipient = v) }
    fun onSeal(v: String) { _state.value = _state.value.copy(seal = v) }
    fun onSignatureChanged(has: Boolean) { _state.value = _state.value.copy(hasSignature = has) }
    fun clearSignature() { _state.value = _state.value.copy(clearKey = _state.value.clearKey + 1, hasSignature = false) }

    fun submit(onDone: () -> Unit) {
        val s = _state.value
        _state.value = s.copy(isSubmitting = true)
        viewModelScope.launch {
            tripRepository.submitPod(
                ProofOfDelivery(
                    tripId = tripId,
                    photoRefs = List(s.photoCount) { "file://pod-photo-$it" },
                    recipientName = s.recipient,
                    sealNumber = s.seal,
                    signatureRef = "file://pod-signature",
                    capturedAtMillis = Clock.System.now().toEpochMilliseconds(),
                ),
            )
            _state.value = _state.value.copy(isSubmitting = false)
            onDone()
        }
    }
}
