package org.totschnig.myexpenses.activity

import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.widget.AdapterView
import androidx.lifecycle.ViewModelProvider
import com.evernote.android.state.State
import org.apache.commons.csv.CSVRecord
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.db2.loadAccount
import org.totschnig.myexpenses.dialog.ConfirmationDialogFragment
import org.totschnig.myexpenses.dialog.ConfirmationDialogFragment.ConfirmationDialogListener
import org.totschnig.myexpenses.dialog.MessageDialogFragment
import org.totschnig.myexpenses.export.qif.QifDateFormat
import org.totschnig.myexpenses.fragment.CsvImportDataFragment
import org.totschnig.myexpenses.fragment.CsvImportParseFragment
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.model2.Account
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.viewmodel.CsvImportViewModel
import java.io.FileNotFoundException
import javax.inject.Inject


class CsvImportActivity : TabbedActivity(), ConfirmationDialogListener {
    @JvmField
    @State
    var dataReady = false

    @JvmField
    @State
    var mUsageRecorded = false

    @JvmField
    @State
    var idle = true

    @Inject
    lateinit var repository: Repository

    private fun setDataReady() {
        dataReady = true
        mSectionsPagerAdapter.notifyDataSetChanged()
    }

    private lateinit var csvImportViewModel: CsvImportViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = getString(R.string.pref_import_title, "CSV")
        csvImportViewModel = ViewModelProvider(this)[CsvImportViewModel::class.java]
        with(injector) {
            inject(csvImportViewModel)
            inject(this@CsvImportActivity)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val allowed = parseFragment.isReady && idle
        menu.findItem(R.id.PARSE_COMMAND)?.isEnabled = allowed
        menu.findItem(R.id.IMPORT_COMMAND)?.isEnabled = allowed
        super.onPrepareOptionsMenu(menu)
        return true
    }

    override fun dispatchCommand(command: Int, tag: Any?) = when {
        super.dispatchCommand(command, tag) -> true
        command == R.id.CLOSE_COMMAND -> {
            finish()
            true
        }
        else -> false
    }

    private fun shouldGoBack() = if (binding.viewPager.currentItem == 1) {
        binding.viewPager.currentItem = 0
        false
    } else true

    override fun doHome() {
        if (shouldGoBack()) super.doHome()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (shouldGoBack()) super.onBackPressed()
    }

    override fun setupTabs() {
        //we only add the first tab, the second one once data has been parsed
        addTab(0)
        if (dataReady) {
            addTab(1)
        }
    }

    private fun addTab(index: Int) {
        when (index) {
            0 -> mSectionsPagerAdapter.addFragment(
                CsvImportParseFragment.newInstance(),
                getString(R.string.menu_parse)
            )
            1 -> mSectionsPagerAdapter.addFragment(
                CsvImportDataFragment.newInstance(),
                getString(R.string.csv_import_preview)
            )
        }
    }

    override fun onPositive(args: Bundle, checked: Boolean) {
        super.onPositive(args, checked)
        if (args.getInt(ConfirmationDialogFragment.KEY_COMMAND_POSITIVE) == R.id.SET_HEADER_COMMAND) {
            (supportFragmentManager.findFragmentByTag(
                mSectionsPagerAdapter.getFragmentName(1)
            ) as? CsvImportDataFragment)?.setHeader(args.getInt(CsvImportDataFragment.KEY_HEADER_LINE_POSITION))
        }
    }

    private fun showProgress(total: Int = 0, progress: Int = 0) {
        showProgressSnackBar(getString(R.string.pref_import_title, "CSV"), total, progress)
        idle = false
        invalidateOptionsMenu()
    }

    private fun hideProgress() {
        dismissSnackBar()
        idle = true
        invalidateOptionsMenu()
    }

    fun parseFile(uri: Uri, delimiter: Char, encoding: String) {
        showProgress()
        csvImportViewModel.parseFile(uri, delimiter, encoding).observe(this) { result ->
            hideProgress()
            result.onSuccess { data ->
                if (data.isNotEmpty()) {
                    if (!dataReady) {
                        addTab(1)
                        setDataReady()
                    }
                    (supportFragmentManager.findFragmentByTag(
                        mSectionsPagerAdapter.getFragmentName(1)
                    ) as? CsvImportDataFragment)?.let {
                        it.setData(data)
                        binding.viewPager.currentItem = 1
                    }
                } else {
                    showSnackBar(R.string.parse_error_no_data_found)
                }
            }.onFailure {
                showSnackBar(
                    when (it) {
                        is FileNotFoundException -> getString(
                            R.string.parse_error_file_not_found,
                            uri
                        )
                        else -> getString(R.string.parse_error_other_exception, it.message)
                    }
                )
            }
        }
    }

    fun importData(dataSet: List<CSVRecord>, columnToFieldMap: IntArray, discardedRows: Int) {
        val totalToImport = dataSet.size
        accountId.takeIf { it != AdapterView.INVALID_ROW_ID }?.also { accountId ->
            showProgress(total = totalToImport)
            csvImportViewModel.progress.observe(this) {
                showProgress(total = totalToImport, progress = it)
            }
            csvImportViewModel.importData(
                dataSet,
                columnToFieldMap,
                dateFormat,
                parseFragment.autoFillCategories
            ) {
                if (accountId == 0L) {
                    Account(
                        label = getString(R.string.pref_import_title, "CSV"),
                        currency = currency,
                        openingBalance = 0,
                        type = accountType
                    ).createIn(repository)
                } else {
                    repository.loadAccount(accountId)!!
                }
            }.observe(this) { result ->
                hideProgress()
                result.onSuccess {
                    if (!mUsageRecorded) {
                        recordUsage(ContribFeature.CSV_IMPORT)
                        mUsageRecorded = true
                    }
                    val success = it.first
                    val failure: Int = it.second
                    val count: Int = success.first
                    val label = success.second
                    var msg = "${getString(R.string.import_transactions_success, count, label)}."
                    if (failure > 0) {
                        msg += " ${getString(R.string.csv_import_records_failed, failure)}"
                    }
                    if (discardedRows > 0) {
                        msg += " ${getString(R.string.csv_import_records_discarded, discardedRows)}"
                    }
                    showMessage(
                        msg,
                        neutral = MessageDialogFragment.nullButton(R.string.button_label_continue),
                        positive = MessageDialogFragment.Button(
                            R.string.button_label_close,
                            R.id.CLOSE_COMMAND,
                            null
                        )
                    )
                }.onFailure {
                    showSnackBar(it.message ?: it.javaClass.simpleName)
                }
            }
        } ?: kotlin.run {
            val exception = Exception("No account selected")
            CrashHandler.report(exception)
            showSnackBar(exception.message!!)
        }
    }

    private val parseFragment: CsvImportParseFragment
        get() = supportFragmentManager.findFragmentByTag(
            mSectionsPagerAdapter.getFragmentName(0)
        ) as CsvImportParseFragment

    val accountId: Long
        get() {
            return parseFragment.getSelectedAccountId()
        }

    val currency: String
        get() = parseFragment.getSelectedCurrency()

    val dateFormat: QifDateFormat
        get() {
            return parseFragment.dateFormat
        }

    val accountType: AccountType
        get() {
            return parseFragment.accountType
        }
}