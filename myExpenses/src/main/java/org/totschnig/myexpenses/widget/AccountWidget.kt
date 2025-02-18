package org.totschnig.myexpenses.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.ExpenseEdit
import org.totschnig.myexpenses.activity.MyExpenses
import org.totschnig.myexpenses.contract.TransactionsContract
import org.totschnig.myexpenses.fragment.AccountWidgetConfigurationFragment
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.DatabaseConstants
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.util.doAsync
import org.totschnig.myexpenses.util.safeMessage

const val CLICK_ACTION_NEW_TRANSACTION = "newTransaction"
const val CLICK_ACTION_NEW_TRANSFER = "newTransfer"
const val CLICK_ACTION_NEW_SPLIT = "newSplit"

class AccountWidget :
    AbstractWidget(AccountWidgetService::class.java, PrefKey.PROTECTION_ENABLE_ACCOUNT_WIDGET) {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WIDGET_LIST_DATA_CHANGED) {
            doAsync {
                intent.extras?.getIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                    ?.forEach { appWidgetId ->
                        val accountId = AccountRemoteViewsFactory.accountId(context, appWidgetId)
                        if (accountId != Long.MAX_VALUE.toString()) {
                            updateSingleAccountWidget(
                                context,
                                AppWidgetManager.getInstance(context),
                                appWidgetId,
                                accountId
                            )
                        }
                    }
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        doAsync {
            super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        doAsync {
            super.onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    override val emptyTextResourceId = R.string.no_accounts

    private fun updateSingleAccountWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        accountId: String
    ) {
        val widget = kotlin.runCatching {
            AccountRemoteViewsFactory.buildCursor(context, accountId)
        }.mapCatching {
            it?.use { cursor ->
                if (cursor.moveToFirst()) {
                    RemoteViews(context.packageName, AbstractRemoteViewsFactory.rowLayout).also { widget ->
                        AccountRemoteViewsFactory.populate(
                            context = context,
                            currencyContext = currencyContext,
                            remoteViews = widget,
                            cursor = cursor,
                            sumColumn = AccountRemoteViewsFactory.sumColumn(context, appWidgetId),
                            availableWidth = availableWidth(context, appWidgetManager, appWidgetId),
                            clickInfo = Pair(appWidgetId, clickBaseIntent(context))
                        )
                    }
                } else {
                    throw Exception(context.getString(R.string.account_deleted))
                }
            } ?: throw Exception("Cursor returned null")
        }.getOrElse {
            RemoteViews(context.packageName, R.layout.widget_list).apply {
                setTextViewText(R.id.emptyView, it.safeMessage)
            }
        }
        appWidgetManager.updateAppWidget(appWidgetId, widget)
    }

    override fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val accountId = AccountRemoteViewsFactory.accountId(context, appWidgetId)
        if (accountId != Long.MAX_VALUE.toString() && !isProtected(context)) {
            updateSingleAccountWidget(context, appWidgetManager, appWidgetId, accountId)
        } else {
            super.updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun handleWidgetClick(context: Context, intent: Intent) {
        val accountId = intent.getLongExtra(DatabaseConstants.KEY_ROWID, 0)
        context.startActivity(when (val clickAction = intent.getStringExtra(KEY_CLICK_ACTION)) {
            null -> Intent(context, MyExpenses::class.java).apply {
                 flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                 putExtra(DatabaseConstants.KEY_ROWID, accountId)
             }
            else -> Intent(context, ExpenseEdit::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (accountId < 0) {
                    putExtra(
                        DatabaseConstants.KEY_CURRENCY,
                        intent.getStringExtra(DatabaseConstants.KEY_CURRENCY)
                    )
                } else {
                    putExtra(DatabaseConstants.KEY_ACCOUNTID, accountId)
                }
                putExtra(EXTRA_START_FROM_WIDGET, true)
                putExtra(EXTRA_START_FROM_WIDGET_DATA_ENTRY, true)
                putExtra(
                    TransactionsContract.Transactions.OPERATION_TYPE, when (clickAction) {
                        CLICK_ACTION_NEW_TRANSACTION -> TransactionsContract.Transactions.TYPE_TRANSACTION
                        CLICK_ACTION_NEW_TRANSFER -> TransactionsContract.Transactions.TYPE_TRANSFER
                        CLICK_ACTION_NEW_SPLIT -> TransactionsContract.Transactions.TYPE_SPLIT
                        else -> throw IllegalArgumentException()
                    }
                )
            }
        })
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            AccountWidgetConfigurationFragment.clearPreferences(context, appWidgetId)
        }
    }

    companion object {
        val OBSERVED_URIS = arrayOf(
            TransactionProvider.ACCOUNTS_URI, //if color changes
            TransactionProvider.TRANSACTIONS_URI
        )
    }
}