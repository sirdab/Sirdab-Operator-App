package co.sirdab.driver.shared.core.ui.components

import co.sirdab.driver.shared.core.model.DriverDocumentKind
import co.sirdab.driver.shared.core.model.Nationality
import co.sirdab.driver.shared.core.model.VerificationDocumentKind
import co.sirdab.driver.shared.core.ui.generated.resources.doc_driving_licence
import co.sirdab.driver.shared.core.ui.generated.resources.doc_national_id_short
import co.sirdab.driver.shared.core.ui.generated.resources.doc_iqama
import co.sirdab.driver.shared.core.ui.generated.resources.doc_istimara
import co.sirdab.driver.shared.core.ui.generated.resources.doc_truck_photo
import co.sirdab.driver.shared.core.ui.generated.resources.doc_vehicle_registration
import co.sirdab.driver.shared.core.ui.generated.resources.nat_bd
import co.sirdab.driver.shared.core.ui.generated.resources.nat_eg
import co.sirdab.driver.shared.core.ui.generated.resources.nat_er
import co.sirdab.driver.shared.core.ui.generated.resources.nat_et
import co.sirdab.driver.shared.core.ui.generated.resources.nat_in
import co.sirdab.driver.shared.core.ui.generated.resources.nat_jo
import co.sirdab.driver.shared.core.ui.generated.resources.nat_lk
import co.sirdab.driver.shared.core.ui.generated.resources.nat_np
import co.sirdab.driver.shared.core.ui.generated.resources.nat_ph
import co.sirdab.driver.shared.core.ui.generated.resources.nat_pk
import co.sirdab.driver.shared.core.ui.generated.resources.nat_ps
import co.sirdab.driver.shared.core.ui.generated.resources.nat_sa
import co.sirdab.driver.shared.core.ui.generated.resources.nat_sd
import co.sirdab.driver.shared.core.ui.generated.resources.nat_sy
import co.sirdab.driver.shared.core.ui.generated.resources.nat_tr
import co.sirdab.driver.shared.core.ui.generated.resources.nat_ye
import co.sirdab.driver.shared.core.ui.generated.resources.err_not_provisioned
import co.sirdab.driver.shared.core.ui.generated.resources.err_not_a_driver
import co.sirdab.driver.shared.core.ui.generated.resources.err_phone_missing
import co.sirdab.driver.shared.core.ui.generated.resources.err_sign_in_failed
import co.sirdab.driver.shared.core.ui.generated.resources.err_document_missing
import co.sirdab.driver.shared.core.ui.generated.resources.err_document_too_large
import co.sirdab.driver.shared.core.ui.generated.resources.err_document_type
import co.sirdab.driver.shared.core.ui.generated.resources.err_driver_suspended
import co.sirdab.driver.shared.core.ui.generated.resources.err_last_truck
import co.sirdab.driver.shared.core.ui.generated.resources.err_unavailable
import co.sirdab.driver.shared.core.ui.generated.resources.err_manages_organization
import co.sirdab.driver.shared.core.ui.generated.resources.err_not_a_bidder
import co.sirdab.driver.shared.core.ui.generated.resources.err_unsent_work
import co.sirdab.driver.shared.core.ui.generated.resources.err_workspace_suspended
import co.sirdab.driver.shared.core.ui.generated.resources.equip_body_box
import co.sirdab.driver.shared.core.ui.generated.resources.equip_body_open
import co.sirdab.driver.shared.core.ui.generated.resources.equip_body_flatbed
import co.sirdab.driver.shared.core.ui.generated.resources.equip_body_curtain_side
import co.sirdab.driver.shared.core.ui.generated.resources.equip_body_lowbed
import co.sirdab.driver.shared.core.ui.generated.resources.equip_body_tanker
import co.sirdab.driver.shared.core.ui.generated.resources.equip_body_container
import co.sirdab.driver.shared.core.ui.generated.resources.equip_body_tipper
import co.sirdab.driver.shared.core.ui.generated.resources.equip_size_pickup
import co.sirdab.driver.shared.core.ui.generated.resources.equip_size_light
import co.sirdab.driver.shared.core.ui.generated.resources.equip_size_medium
import co.sirdab.driver.shared.core.ui.generated.resources.equip_size_heavy
import co.sirdab.driver.shared.core.ui.generated.resources.equip_size_trailer
import co.sirdab.driver.shared.core.ui.generated.resources.equip_temp_ambient
import co.sirdab.driver.shared.core.ui.generated.resources.equip_temp_chilled
import co.sirdab.driver.shared.core.ui.generated.resources.equip_temp_frozen
import co.sirdab.driver.shared.core.ui.generated.resources.equip_temp_multi
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.EquipmentBody
import co.sirdab.driver.shared.core.model.EquipmentSize
import co.sirdab.driver.shared.core.model.EquipmentTemperature
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

fun EquipmentBody.labelRes(): StringResource = when (this) {
    EquipmentBody.BOX -> Res.string.equip_body_box
    EquipmentBody.OPEN -> Res.string.equip_body_open
    EquipmentBody.FLATBED -> Res.string.equip_body_flatbed
    EquipmentBody.CURTAIN_SIDE -> Res.string.equip_body_curtain_side
    EquipmentBody.LOWBED -> Res.string.equip_body_lowbed
    EquipmentBody.TANKER -> Res.string.equip_body_tanker
    EquipmentBody.CONTAINER -> Res.string.equip_body_container
    EquipmentBody.TIPPER -> Res.string.equip_body_tipper
}

fun EquipmentSize.labelRes(): StringResource = when (this) {
    EquipmentSize.PICKUP -> Res.string.equip_size_pickup
    EquipmentSize.LIGHT -> Res.string.equip_size_light
    EquipmentSize.MEDIUM -> Res.string.equip_size_medium
    EquipmentSize.HEAVY -> Res.string.equip_size_heavy
    EquipmentSize.TRAILER -> Res.string.equip_size_trailer
}

fun EquipmentTemperature.labelRes(): StringResource = when (this) {
    EquipmentTemperature.AMBIENT -> Res.string.equip_temp_ambient
    EquipmentTemperature.CHILLED -> Res.string.equip_temp_chilled
    EquipmentTemperature.FROZEN -> Res.string.equip_temp_frozen
    EquipmentTemperature.MULTI -> Res.string.equip_temp_multi
}

/** A truck in the platform's three axes, size first the way a driver says it. An unclassified axis is left out. */
@Composable
fun equipmentLabel(
    bodyType: EquipmentBody?,
    sizeClass: EquipmentSize?,
    temperature: EquipmentTemperature?,
): String = listOfNotNull(sizeClass?.labelRes(), bodyType?.labelRes(), temperature?.labelRes())
    .map { stringResource(it) }
    .joinToString(" · ")

/** The words for a failure the data layer only named. */
fun AppErrorReason.labelRes(): StringResource = when (this) {
    AppErrorReason.NOT_PROVISIONED -> Res.string.err_not_provisioned
    AppErrorReason.NOT_A_DRIVER -> Res.string.err_not_a_driver
    AppErrorReason.PHONE_MISSING -> Res.string.err_phone_missing
    AppErrorReason.SIGN_IN_FAILED -> Res.string.err_sign_in_failed
    AppErrorReason.UNAVAILABLE -> Res.string.err_unavailable
    AppErrorReason.DRIVER_SUSPENDED -> Res.string.err_driver_suspended
    AppErrorReason.WORKSPACE_SUSPENDED -> Res.string.err_workspace_suspended
    AppErrorReason.LAST_TRUCK -> Res.string.err_last_truck
    AppErrorReason.DOCUMENT_TOO_LARGE -> Res.string.err_document_too_large
    AppErrorReason.DOCUMENT_TYPE -> Res.string.err_document_type
    AppErrorReason.DOCUMENT_MISSING -> Res.string.err_document_missing
    AppErrorReason.UNSENT_WORK -> Res.string.err_unsent_work
    AppErrorReason.NOT_A_BIDDER -> Res.string.err_not_a_bidder
    AppErrorReason.MANAGES_ORGANIZATION -> Res.string.err_manages_organization
}

fun Nationality.labelRes(): StringResource = when (this) {
    Nationality.SAUDI -> Res.string.nat_sa
    Nationality.BANGLADESHI -> Res.string.nat_bd
    Nationality.EGYPTIAN -> Res.string.nat_eg
    Nationality.ERITREAN -> Res.string.nat_er
    Nationality.ETHIOPIAN -> Res.string.nat_et
    Nationality.INDIAN -> Res.string.nat_in
    Nationality.JORDANIAN -> Res.string.nat_jo
    Nationality.NEPALI -> Res.string.nat_np
    Nationality.PAKISTANI -> Res.string.nat_pk
    Nationality.PALESTINIAN -> Res.string.nat_ps
    Nationality.FILIPINO -> Res.string.nat_ph
    Nationality.SRI_LANKAN -> Res.string.nat_lk
    Nationality.SUDANESE -> Res.string.nat_sd
    Nationality.SYRIAN -> Res.string.nat_sy
    Nationality.TURKISH -> Res.string.nat_tr
    Nationality.YEMENI -> Res.string.nat_ye
}

fun DriverDocumentKind.labelRes(): StringResource = when (this) {
    DriverDocumentKind.NATIONAL_ID -> Res.string.doc_national_id_short
    DriverDocumentKind.IQAMA -> Res.string.doc_iqama
    DriverDocumentKind.DRIVING_LICENCE -> Res.string.doc_driving_licence
    DriverDocumentKind.VEHICLE_REGISTRATION -> Res.string.doc_vehicle_registration
}

/**
 * The fleet's five document kinds, which are not sign-up's three.
 *
 * `istimara` and `vehicle_registration` are the same piece of paper under two names, one per
 * surface, so they share a label rather than teaching the driver the difference.
 */
fun VerificationDocumentKind.labelRes(): StringResource = when (this) {
    VerificationDocumentKind.NATIONAL_ID -> Res.string.doc_national_id_short
    VerificationDocumentKind.IQAMA -> Res.string.doc_iqama
    VerificationDocumentKind.DRIVING_LICENCE -> Res.string.doc_driving_licence
    VerificationDocumentKind.ISTIMARA -> Res.string.doc_istimara
    VerificationDocumentKind.TRUCK_PHOTO -> Res.string.doc_truck_photo
}
