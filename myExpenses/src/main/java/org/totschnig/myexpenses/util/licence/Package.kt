package org.totschnig.myexpenses.util.licence

import android.content.Context
import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.util.CurrencyFormatter
import org.totschnig.myexpenses.util.formatMoney
import java.util.*

@Keep
sealed class Package(val defaultPrice: Long) : Parcelable {
    open val optionName = "Licence"

    open fun payPalButtonId(isSandBox: Boolean) =
        if (isSandBox) "TURRUESSCUG8N" else "LBUDF8DSWJAZ8"

    fun getFormattedPrice(
        context: Context,
        currencyFormatter: CurrencyFormatter,
        currencyUnit: CurrencyUnit,
        withExtra: Boolean
    ): String {
        val formatted = getFormattedPriceRaw(currencyUnit, currencyFormatter)
        return getFormattedPrice(context, formatted, withExtra)
    }

    open fun getFormattedPrice(context: Context, formatted: String, withExtra: Boolean) = formatted

    fun getFormattedPriceRaw(currencyUnit: CurrencyUnit, currencyFormatter: CurrencyFormatter) =
        currencyFormatter.formatMoney(Money(currencyUnit, defaultPrice))

    @Parcelize
    @Keep
    object Contrib : Package(880)

    @Parcelize
    @Keep
    object Upgrade : Package(300)

    @Parcelize
    @Keep
    object Extended : Package(1100)
}

@Suppress("ClassName")
@Keep
sealed class ProfessionalPackage(defaultPrice: Long, val duration: Int) : Package(defaultPrice) {
    @Parcelize
    @Keep
    object Professional_1 : ProfessionalPackage(100, 1)

    @Parcelize
    @Keep
    object Professional_6 : ProfessionalPackage(500, 6)

    @Parcelize
    @Keep
    object Professional_12 : ProfessionalPackage(800, 12)

    @Parcelize
    @Keep
    object Professional_24 : ProfessionalPackage(1500, 24)

    @Parcelize
    @Keep
    object Amazon : ProfessionalPackage(900, 0)

    fun getDuration(withExtra: Boolean): Int {
        val base = duration
        return if (withExtra) base + DURATION_EXTRA else base
    }

    override fun getFormattedPrice(context: Context, formatted: String, withExtra: Boolean) =
        formatWithDuration(context, formatted, withExtra)

/*    fun getMonthlyPrice(withExtra: Boolean) =
            ceil(defaultPrice.toDouble() / getDuration(withExtra)).toLong()*/

    private fun formatWithDuration(
        context: Context,
        formattedPrice: String?,
        withExtra: Boolean
    ): String {
        val duration = getDuration(withExtra)
        val formattedDuration: String
        var format = "%s (%s)"
        when (duration) {
            1 -> formattedDuration = context.getString(R.string.monthly_plain)
            12 -> formattedDuration = context.getString(R.string.yearly_plain)
            else -> {
                format = "%s / %s"
                formattedDuration = context.getString(R.string.n_months, duration)
            }
        }
        return String.format(format, formattedPrice, formattedDuration)
    }

    companion object {
        /**
         * Extra months credited for professional licence to holders of extended licence
         */
        private const val DURATION_EXTRA = 3
    }
}

@Keep
sealed class AddOnPackage(
    val feature: ContribFeature,
    private val isContribFeature: Boolean = feature.licenceStatus == LicenceStatus.CONTRIB
) : Package(if (isContribFeature) 270 else 430) {

    companion object {
        //We cannot use an initializer here, because the objects we want to list might not be constructed
        //thus giving us a list of nulls:
        //https://youtrack.jetbrains.com/issue/KT-8970/Object-is-uninitialized-null-when-accessed-from-static-context-ex.-companion-object-with-initialization-loop
        val values: List<AddOnPackage>
            get() = listOf(
                SplitTemplate, History, Budget, Ocr, WebUi, CategoryTree,
                AccountsUnlimited, PlansUnlimited, SplitTransaction, Distribution, Print, AdFree,
                CsvImport, Synchronization
            )
    }

    override val optionName = "AddOn"
    val sku: String
        get() = this::class.simpleName!!.lowercase(Locale.ROOT)

    override fun payPalButtonId(isSandBox: Boolean) =
        when {
            isSandBox -> if (isContribFeature) "UAWN7XUQNZ5PS" else "9VF4Z9KSLHXZN"
            isContribFeature -> "48RQY4SKUHTAQ"
            else -> "FNEEWJWU5YJ44"
        }

    @Parcelize
    @Keep
    object SplitTemplate : AddOnPackage(ContribFeature.SPLIT_TEMPLATE)

    @Parcelize
    @Keep
    object History : AddOnPackage(ContribFeature.HISTORY)

    @Parcelize
    @Keep
    object Budget : AddOnPackage(ContribFeature.BUDGET)

    @Parcelize
    @Keep
    object Ocr : AddOnPackage(ContribFeature.OCR)

    @Parcelize
    @Keep
    object WebUi : AddOnPackage(ContribFeature.WEB_UI)

    @Parcelize
    @Keep
    object CategoryTree : AddOnPackage(ContribFeature.CATEGORY_TREE)

    @Parcelize
    @Keep
    object AccountsUnlimited : AddOnPackage(ContribFeature.ACCOUNTS_UNLIMITED)

    @Parcelize
    @Keep
    object PlansUnlimited : AddOnPackage(ContribFeature.PLANS_UNLIMITED)

    @Parcelize
    @Keep
    object SplitTransaction : AddOnPackage(ContribFeature.SPLIT_TRANSACTION)

    @Parcelize
    @Keep
    object Distribution : AddOnPackage(ContribFeature.DISTRIBUTION)

    @Parcelize
    @Keep
    object Print : AddOnPackage(ContribFeature.PRINT)

    @Parcelize
    @Keep
    object AdFree : AddOnPackage(ContribFeature.AD_FREE)

    @Parcelize
    @Keep
    object CsvImport : AddOnPackage(ContribFeature.CSV_IMPORT)

    @Parcelize
    @Keep
    object Synchronization : AddOnPackage(ContribFeature.SYNCHRONIZATION)
}