package dev.lostxposed

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.lostxposed.ui.Ui

/**
 * Displays the report built by [Diagnostics], and lets it be shared.
 *
 * Exportable because the alternative is bug reports made of screenshots and recollection.
 */
class DiagnosticsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        actionBar?.hide()

        val report = Diagnostics.report(this)

        val column = Ui.column(this).apply {
            addView(
                Ui.screenHeader(this@DiagnosticsActivity, "Diagnostics", badgeText = null),
            )
            addView(
                Ui.caption(
                    this@DiagnosticsActivity,
                    "Covers this app process only. Hook-side reports come from logcat.",
                ),
            )
            addView(Ui.spacer(this@DiagnosticsActivity, 14))
            addView(Ui.button(this@DiagnosticsActivity, "Share report") { share(report) })
            addView(Ui.spacer(this@DiagnosticsActivity, 14))
            addView(Ui.mono(this@DiagnosticsActivity, report))
            addView(Ui.spacer(this@DiagnosticsActivity, 24))
        }

        setContentView(Ui.screen(this, column, padTop = true))
    }

    private fun share(report: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Lost Xposed diagnostics")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        startActivity(Intent.createChooser(intent, "Share diagnostics"))
    }
}
