// SPDX-License-Identifier: CC0-1.0
package org.getflourish.beeswax

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

// The About page, and the setup guide before setup. Each link shows its full address first and opens only on Open,
// and only in an app that's allowed. With none (browsers blocked, no wallet), the address is copied instead.
// Copy and that fallback copy an email or wallet address without its "mailto:" or "monero:", so it can be pasted.
// Nothing here needs the code.
object About {
    private const val SITE = "https://beeswax.getflourish.org"
    private const val REPO = "https://github.com/flourish-today/Beeswax"
    private const val EMAIL = "flourish.today@protonmail.com"
    private const val SIMPLEX = "https://smp17.simplex.im/a#OYNzLgLMY-vsrI69LroxLrhc4xkyLGpT2Q5ygNvoaAc"
    private const val MONERO = "82omZG3aUU7gqJhR6dZGSReYvKD7kHFuxj5MGKMAGiJdcSYLnMb9Nky9UBxeuadSSzCNYpwfUueQBdQPEaTXmLH4Tkmbv39"

    fun setUp(activity: MainActivity) {
        val info = activity.packageManager.getPackageInfo(activity.packageName, PackageManager.PackageInfoFlags.of(0))
        activity.findViewById<TextView>(R.id.about_version).text =
            "${activity.applicationInfo.loadLabel(activity.packageManager)} ${info.versionName}"
        activity.findViewById<TextView>(R.id.email).text = EMAIL
        activity.findViewById<TextView>(R.id.monero_address).text = MONERO
        // The license file wraps its lines for a wide screen, so its paragraphs are rejoined to fit the phone.
        val license = activity.resources.openRawResource(R.raw.apache_2_0).bufferedReader().use { it.readText() }
        activity.findViewById<TextView>(R.id.apache_license).text = license.trim().split(Regex("\n\\s*\n"))
            .joinToString("\n\n") { it.trim().replace(Regex("\\s+"), " ") }
        val links = mapOf(
            R.id.website to "$SITE/",
            R.id.setup_guide to "$SITE/setup/",
            R.id.setup_guide_from_setup to "$SITE/setup/",
            R.id.faq to "$SITE/faq/",
            R.id.report_issue to "$REPO/issues",
            R.id.email_open to "mailto:$EMAIL",
            R.id.simplex to SIMPLEX,
            R.id.star to REPO,
            R.id.open_wallet to "monero:$MONERO",
            R.id.learn_monero to "$SITE/monero/",
            R.id.manifesto to "https://www.activism.net/cypherpunk/manifesto.html",
        )
        for ((id, link) in links) activity.findViewById<Button>(id).setOnClickListener { confirm(activity, link) }
    }

    private fun confirm(activity: MainActivity, link: String) {
        activity.show(AlertDialog.Builder(activity)
            .setTitle(R.string.link_title)
            .setMessage(link)
            .setPositiveButton(R.string.link_open) { _, _ -> open(activity, link) }
            .setNeutralButton(R.string.link_copy) { _, _ -> copy(activity, link) }
            .setNegativeButton(android.R.string.cancel, null))
    }

    private fun open(activity: MainActivity, link: String) {
        val uri = Uri.parse(link)
        val action = if (uri.scheme == "mailto") Intent.ACTION_SENDTO else Intent.ACTION_VIEW
        try {
            activity.startActivity(Intent(action, uri))
        } catch (e: Exception) {
            copy(activity, link)
            Toast.makeText(activity, R.string.link_copied, Toast.LENGTH_LONG).show()
        }
    }

    // Android shows its own note when something is copied.
    private fun copy(activity: MainActivity, link: String) {
        val text = link.removePrefix("mailto:").removePrefix("monero:")
        activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(null, text))
    }
}
