package co.sirdab.driver.shared.core.ui.components

import co.sirdab.driver.shared.core.model.CargoType
import co.sirdab.driver.shared.core.model.HandlingFlag
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
