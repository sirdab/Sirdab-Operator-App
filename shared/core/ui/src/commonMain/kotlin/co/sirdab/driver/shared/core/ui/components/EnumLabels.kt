package co.sirdab.driver.shared.core.ui.components

import co.sirdab.driver.shared.core.model.CargoType
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
import co.sirdab.driver.shared.core.model.HandlingFlag
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
import co.sirdab.driver.shared.core.ui.generated.resources.err_unsent_work
import co.sirdab.driver.shared.core.ui.generated.resources.err_workspace_suspended
import co.sirdab.driver.shared.core.ui.generated.resources.tt_dry
import co.sirdab.driver.shared.core.ui.generated.resources.tt_chilled
import co.sirdab.driver.shared.core.ui.generated.resources.tt_frozen
import co.sirdab.driver.shared.core.ui.generated.resources.ts_cargo_van
import co.sirdab.driver.shared.core.ui.generated.resources.ts_open_dyna
import co.sirdab.driver.shared.core.ui.generated.resources.ts_closed_dyna
import co.sirdab.driver.shared.core.ui.generated.resources.ts_open_lorry
import co.sirdab.driver.shared.core.ui.generated.resources.ts_closed_lorry
import co.sirdab.driver.shared.core.ui.generated.resources.ts_winch
import co.sirdab.driver.shared.core.ui.generated.resources.ts_flatbed
import co.sirdab.driver.shared.core.ui.generated.resources.ts_curtain_side
import co.sirdab.driver.shared.core.ui.generated.resources.ts_ltl
import co.sirdab.driver.shared.core.ui.generated.resources.ts_trailer
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.TruckSize
import co.sirdab.driver.shared.core.model.TruckType
import co.sirdab.driver.shared.core.model.VehicleType
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.cargo_container
import co.sirdab.driver.shared.core.ui.generated.resources.cargo_general
import co.sirdab.driver.shared.core.ui.generated.resources.cargo_hazmat
import co.sirdab.driver.shared.core.ui.generated.resources.cargo_livestock
import co.sirdab.driver.shared.core.ui.generated.resources.cargo_oversized
import co.sirdab.driver.shared.core.ui.generated.resources.cargo_reefer
import co.sirdab.driver.shared.core.ui.generated.resources.flag_fragile
import co.sirdab.driver.shared.core.ui.generated.resources.flag_hazmat
import co.sirdab.driver.shared.core.ui.generated.resources.flag_multi_stop
import co.sirdab.driver.shared.core.ui.generated.resources.flag_oversized_permit
import co.sirdab.driver.shared.core.ui.generated.resources.flag_reefer_temp
import co.sirdab.driver.shared.core.ui.generated.resources.flag_tail_lift
import co.sirdab.driver.shared.core.ui.generated.resources.vt_container
import co.sirdab.driver.shared.core.ui.generated.resources.vt_curtain
import co.sirdab.driver.shared.core.ui.generated.resources.vt_dry_van
import co.sirdab.driver.shared.core.ui.generated.resources.vt_flatbed
import co.sirdab.driver.shared.core.ui.generated.resources.vt_lowbed
import co.sirdab.driver.shared.core.ui.generated.resources.vt_reefer
import co.sirdab.driver.shared.core.ui.generated.resources.vt_tanker
import co.sirdab.driver.shared.core.ui.generated.resources.vt_van_3t
import org.jetbrains.compose.resources.StringResource

fun VehicleType.labelRes(): StringResource = when (this) {
    VehicleType.FLATBED -> Res.string.vt_flatbed
    VehicleType.CURTAIN_SIDER -> Res.string.vt_curtain
    VehicleType.REEFER -> Res.string.vt_reefer
    VehicleType.CONTAINER_40FT -> Res.string.vt_container
    VehicleType.DRY_VAN -> Res.string.vt_dry_van
    VehicleType.VAN_3T -> Res.string.vt_van_3t
    VehicleType.LOWBED -> Res.string.vt_lowbed
    VehicleType.TANKER -> Res.string.vt_tanker
}

fun CargoType.labelRes(): StringResource = when (this) {
    CargoType.GENERAL -> Res.string.cargo_general
    CargoType.REEFER -> Res.string.cargo_reefer
    CargoType.HAZMAT -> Res.string.cargo_hazmat
    CargoType.OVERSIZED -> Res.string.cargo_oversized
    CargoType.CONTAINER -> Res.string.cargo_container
    CargoType.LIVESTOCK -> Res.string.cargo_livestock
}

fun HandlingFlag.labelRes(): StringResource = when (this) {
    HandlingFlag.FRAGILE -> Res.string.flag_fragile
    HandlingFlag.REEFER_TEMP -> Res.string.flag_reefer_temp
    HandlingFlag.HAZMAT -> Res.string.flag_hazmat
    HandlingFlag.OVERSIZED_PERMIT -> Res.string.flag_oversized_permit
    HandlingFlag.MULTI_STOP -> Res.string.flag_multi_stop
    HandlingFlag.TAIL_LIFT -> Res.string.flag_tail_lift
}

fun TruckType.labelRes(): StringResource = when (this) {
    TruckType.DRY -> Res.string.tt_dry
    TruckType.CHILLED -> Res.string.tt_chilled
    TruckType.FROZEN -> Res.string.tt_frozen
}

fun TruckSize.labelRes(): StringResource = when (this) {
    TruckSize.CARGO_VAN -> Res.string.ts_cargo_van
    TruckSize.OPEN_DYNA -> Res.string.ts_open_dyna
    TruckSize.CLOSED_DYNA -> Res.string.ts_closed_dyna
    TruckSize.OPEN_LORRY -> Res.string.ts_open_lorry
    TruckSize.CLOSED_LORRY -> Res.string.ts_closed_lorry
    TruckSize.WINCH -> Res.string.ts_winch
    TruckSize.FLATBED -> Res.string.ts_flatbed
    TruckSize.CURTAIN_SIDE -> Res.string.ts_curtain_side
    TruckSize.LTL -> Res.string.ts_ltl
    TruckSize.TRAILER -> Res.string.ts_trailer
}

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
