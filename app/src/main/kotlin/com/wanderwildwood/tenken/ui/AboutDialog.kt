package com.wanderwildwood.tenken.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.tenken.BuildConfig
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.wanderwildwood.tenken.R

/**
 * What this is, what it does with what it knows, and whose work it started from.
 *
 * The line about the car is here because it knows the car's identity number and how it is
 * driven, and a stranger has no way to tell from the outside whether any of it is sent on.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(
            text = stringResource(R.string.about_title, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(10.dp))

        // Longer than a screen, so it steps a page at a time like every list here.
        PagedColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            item { Section(R.string.about_privacy_head, R.string.about_privacy) }
            item { Section(R.string.about_terms_head, R.string.about_terms) }
            item { Section(R.string.about_disclaimer_head, R.string.about_disclaimer) }
            item { Section(R.string.about_sources_head, R.string.about_sources) }
            item { Section(null, R.string.about_after) }
            item {
                Column(Modifier.padding(bottom = 14.dp)) {
                    Heading(R.string.about_licences_head)
                    TextMMD(text = stringResource(R.string.about_licence), style = MaterialTheme.typography.bodyMedium)
                    TextMMD(text = stringResource(R.string.about_libraries), style = MaterialTheme.typography.bodyMedium)
                    TextMMD(text = stringResource(R.string.about_icons), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item { Link(R.string.about_source, "https://github.com/wanderwildwood/tenken") }
            item { Llama() }
        }

        Spacer(Modifier.height(14.dp))
        OutlinedButtonMMD(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = stringResource(R.string.about_close), style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun Heading(title: Int) {
    TextMMD(
        text = stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun Section(title: Int?, body: Int) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        if (title != null) Heading(title)
        TextMMD(text = stringResource(body), style = MaterialTheme.typography.bodyMedium)
    }
}

/** A web address that opens in the browser, if the phone has one, on a 48 dp row. */
@Composable
private fun Link(label: Int, address: String) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { open(context, address) },
    ) {
        TextMMD(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = TextDecoration.Underline,
        )
    }
}

private fun open(context: android.content.Context, address: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address)))
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.about_no_browser), Toast.LENGTH_SHORT).show()
    }
}

/**
 * A llama at the foot of the About, which opens the page a donation goes to.
 * The site's address sits at the start of the same line and opens the site; the llama and its words open the page.
 *
 * Three words rather than an address: a verb and an object, so what happens when you press
 * them is not a surprise even though the page is not named. The drawing is his own, and it is
 * ink rather than an emoji, which is a colour glyph and reaches the panel as a pale smudge.
 *
 * The Kompakt may have nothing registered for a web address, so the intent is allowed to fail
 * quietly rather than take the dialog down with it.
 */
@Composable
private fun Llama() {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextMMD(
            text = "wanderthe.dev",
            style = MaterialTheme.typography.bodyMedium,
            // The site's address opens the site, the way the llama beside it opens its page.
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable { open(context, "https://wanderthe.dev") }
                .wrapContentHeight(Alignment.CenterVertically),
        )
        Spacer(Modifier.width(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 48.dp)
                // Straight to the checkout. The short square.link form, not the long
                // checkout.square.site address it redirects to: the site links to the short one,
                // so a regenerated checkout follows it and a published app does not break.
                .clickable { open(context, "https://square.link/u/AGu8oT10") },
        ) {
            Image(
                painter = painterResource(R.drawable.llama),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(6.dp))
            TextMMD(text = stringResource(R.string.about_feed_the_llamas), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
