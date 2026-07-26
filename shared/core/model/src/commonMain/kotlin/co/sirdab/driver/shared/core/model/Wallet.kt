package co.sirdab.driver.shared.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class TxnType { EARNING, PAYOUT, COMMISSION, ADJUSTMENT }

@Serializable
enum class TxnStatus { PENDING, SETTLED }

@Serializable
data class WalletTxn(
    val id: String,
    val type: TxnType,
    val amountSar: Int,
    val status: TxnStatus,
    val descriptionEn: String,
    val descriptionAr: String,
    val createdAtMillis: Long,
)

@Serializable
data class WalletState(
    val availableSar: Int = 0,
    val pendingSar: Int = 0,
    val transactions: List<WalletTxn> = emptyList(),
)
