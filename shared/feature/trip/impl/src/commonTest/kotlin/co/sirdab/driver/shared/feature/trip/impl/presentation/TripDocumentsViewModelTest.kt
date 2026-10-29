package co.sirdab.driver.shared.feature.trip.impl.presentation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import co.sirdab.driver.shared.core.analytics.testing.RecordingAnalytics
import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DriverTrip
import co.sirdab.driver.shared.core.model.Page
import co.sirdab.driver.shared.core.platform.documents.DocumentViewer
import co.sirdab.driver.shared.feature.trip.api.DownloadedDocument
import co.sirdab.driver.shared.feature.trip.api.DriverTripRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

private val PDF = DownloadedDocument(ByteArray(3 * 1024), "SO-1042-outbound_slip.pdf")

private class FakeDocuments(
    private val result: AppResult<DownloadedDocument> = AppResult.Success(PDF),
    /** Held open by a test that needs the download still in flight; already through by default. */
    private val gate: CompletableDeferred<Unit> = CompletableDeferred(Unit),
) : DriverTripRepository {
    val asked = mutableListOf<Pair<String, String>>()

    override suspend fun trips(cursor: String?, limit: Int): AppResult<Page<DriverTrip>> =
        error("documents never list trips")

    override suspend fun trip(id: String): AppResult<DriverTrip> = error("documents never read the trip")

    override suspend fun legDocument(legId: String, key: String): AppResult<DownloadedDocument> {
        asked += legId to key
        gate.await()
        return result
    }
}

private class FakeViewer(private val opens: Boolean = true) : DocumentViewer {
    val shown = mutableListOf<String>()

    override suspend fun showPdf(bytes: ByteArray, fileName: String): Boolean {
        shown += fileName
        return opens
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TripDocumentsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val analytics = RecordingAnalytics()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `hands the downloaded pdf to the phone's viewer under the server's name`() = runTest(dispatcher) {
        val viewer = FakeViewer()
        val vm = TripDocumentsViewModel("t1", FakeDocuments(), viewer, analytics)

        vm.open("l1", "outbound_slip")
        testScheduler.advanceUntilIdle()

        assertThat(viewer.shown).isEqualTo(listOf("SO-1042-outbound_slip.pdf"))
        assertThat(vm.state.value).isEqualTo(TripDocumentsUiState())
        assertThat(analytics.events.single().name).isEqualTo("Order Document Opened")
        assertThat(analytics.events.single().properties["document_key"]).isEqualTo("outbound_slip")
        assertThat(analytics.events.single().properties["kilobytes"]).isEqualTo(3)
    }

    @Test
    fun `shows which document is on its way while it comes`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val vm = TripDocumentsViewModel("t1", FakeDocuments(gate = gate), FakeViewer(), analytics)

        vm.open("l1", "outbound_slip")
        testScheduler.advanceUntilIdle()

        assertThat(vm.state.value.downloading).isEqualTo(DocumentRef("l1", "outbound_slip"))
        gate.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertThat(vm.state.value.downloading).isNull()
    }

    @Test
    fun `ignores a second tap while one is still coming`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeDocuments(gate = gate)
        val vm = TripDocumentsViewModel("t1", repository, FakeViewer(), analytics)

        vm.open("l1", "outbound_slip")
        testScheduler.advanceUntilIdle()
        vm.open("l1", "ab_trip")
        gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertThat(repository.asked).isEqualTo(listOf("l1" to "outbound_slip"))
    }

    @Test
    fun `keeps the reason a document failed against that document`() = runTest(dispatcher) {
        val refused = AppResult.Failure(AppError("refused", reason = AppErrorReason.DOCUMENT_UNAVAILABLE))
        val viewer = FakeViewer()
        val vm = TripDocumentsViewModel("t1", FakeDocuments(refused), viewer, analytics)

        vm.open("l1", "outbound_slip")
        testScheduler.advanceUntilIdle()

        assertThat(vm.state.value.failure).isEqualTo(
            DocumentFailure(DocumentRef("l1", "outbound_slip"), AppErrorReason.DOCUMENT_UNAVAILABLE),
        )
        assertThat(viewer.shown).isEqualTo(emptyList())
        assertThat(analytics.events.single().name).isEqualTo("Order Document Failed")
        assertThat(analytics.events.single().properties["error_reason"]).isEqualTo("document_unavailable")
    }

    @Test
    fun `says so when nothing on the phone opens a pdf`() = runTest(dispatcher) {
        val vm = TripDocumentsViewModel("t1", FakeDocuments(), FakeViewer(opens = false), analytics)

        vm.open("l1", "outbound_slip")
        testScheduler.advanceUntilIdle()

        assertThat(vm.state.value.failure?.reason).isEqualTo(AppErrorReason.NO_DOCUMENT_VIEWER)
    }

    @Test
    fun `trying again clears the last failure`() = runTest(dispatcher) {
        val offline = FakeDocuments(AppResult.Failure(AppError("network unavailable")))
        val vm = TripDocumentsViewModel("t1", offline, FakeViewer(), analytics)
        vm.open("l1", "outbound_slip")
        testScheduler.advanceUntilIdle()
        // No named reason: the screen says to check the signal.
        assertThat(vm.state.value.failure?.document).isEqualTo(DocumentRef("l1", "outbound_slip"))
        assertThat(vm.state.value.failure?.reason).isNull()

        vm.open("l1", "outbound_slip")

        // At the tap, before the second attempt has even started.
        assertThat(vm.state.value.failure).isNull()
        assertThat(vm.state.value.downloading).isEqualTo(DocumentRef("l1", "outbound_slip"))
    }
}
