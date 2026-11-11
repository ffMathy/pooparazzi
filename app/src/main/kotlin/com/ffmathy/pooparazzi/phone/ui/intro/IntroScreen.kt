package com.ffmathy.pooparazzi.phone.ui.intro

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.tokens.LocalXPalette
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.ui.account.PrimaryButton
import kotlinx.coroutines.launch

/** The introduction's pages, in order: what the app is, then how to log, fix, read and reach it. */
enum class IntroPage(@StringRes val title: Int, @StringRes val body: Int) {
    WELCOME(R.string.intro_welcome_title, R.string.intro_welcome_body),
    SLEEP(R.string.intro_sleep_title, R.string.intro_sleep_body),
    LOG(R.string.intro_log_title, R.string.intro_log_body),
    FIX(R.string.intro_fix_title, R.string.intro_fix_body),
    PATTERNS(R.string.intro_patterns_title, R.string.intro_patterns_body),
    EVERYWHERE(R.string.intro_everywhere_title, R.string.intro_everywhere_body),
}

/**
 * The introduction wired to the system Back: Back turns to the page before, and on the first page closes the
 * introduction like Skip. [onDone] once it is finished or skipped.
 */
@Composable
fun IntroRoute(onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    val pager = rememberPagerState { IntroPage.entries.size }
    val scope = rememberCoroutineScope()
    BackHandler {
        if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } else done()
    }
    IntroScreen(onDone = { done() }, pager = pager)
}

/**
 * The introduction (DESIGN_SPEC §3.3.5): a short, skippable tour of how to use the app, swiped or stepped with Next.
 * Each page has an illustration of the app's own icons in their event colours, a heading and a paragraph. Skip (top
 * end) closes it from any page; the last page's Next reads "Get started". It follows the first run's "Your baby"
 * screen once per phone, and Settings › About › Introduction shows it again.
 */
@Composable
fun IntroScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    pager: PagerState = rememberPagerState { IntroPage.entries.size },
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val colors = MaterialTheme.colorScheme
    val bottomInset = with(LocalDensity.current) { windowInsets.getBottom(this).toDp() }
    val scope = rememberCoroutineScope()
    val pages = IntroPage.entries
    val last = pager.currentPage == pages.lastIndex
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(bottom = 24.dp + bottomInset),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!last) {
                TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.intro_skip), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                }
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
            IntroPageContent(pages[index])
        }
        PageDots(current = pager.currentPage, count = pages.size, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(20.dp))
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PrimaryButton(
                stringResource(if (last) R.string.intro_done else R.string.intro_next),
                onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                enabled = true,
            )
            // Always there, so the Next button never moves; invisible to touch and TalkBack on the first page.
            val first = pager.currentPage == 0
            TextButton(
                onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                enabled = !first,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(
                    stringResource(R.string.intro_back),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (first) Color.Transparent else colors.onSurface,
                )
            }
        }
    }
}

/** One page: the illustration, centred, then the heading and the paragraph; scrolls with large fonts. */
@Composable
private fun IntroPageContent(page: IntroPage) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Illustration(page)
        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(page.title),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(page.body),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The page's picture: the app's own icons on discs of their event colours (decorative; the text says it all). */
@Composable
private fun Illustration(page: IntroPage) {
    val events = LocalXPalette.current.events
    val colors = MaterialTheme.colorScheme
    val sleep = Tone(events.sleep.container, events.sleep.strong)
    val feed = Tone(events.feed.container, events.feed.strong)
    val diaper = Tone(events.diaper.container, events.diaper.strong)
    val neutral = Tone(colors.surfaceVariant, colors.onSurface)
    when (page) {
        IntroPage.WELCOME -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Disc(XIcons.sleep, sleep, 80.dp)
            Disc(XIcons.feed, feed, 104.dp)
            Disc(XIcons.diaper, diaper, 80.dp)
        }
        IntroPage.SLEEP -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Disc(XIcons.sleep, sleep, 112.dp)
            Disc(XIcons.wake, sleep, 80.dp)
        }
        IntroPage.LOG -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Disc(XIcons.feed, feed, 104.dp)
            Disc(XIcons.diaper, diaper, 104.dp)
        }
        IntroPage.FIX -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Disc(XIcons.undo, neutral, 80.dp)
            Disc(XIcons.edit, neutral, 112.dp)
        }
        IntroPage.PATTERNS -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Disc(XIcons.upcoming, feed, 80.dp)
            Disc(XIcons.history, neutral, 104.dp)
            Disc(XIcons.chart, neutral, 80.dp)
        }
        IntroPage.EVERYWHERE -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Disc(XIcons.phoneSynced, neutral, 104.dp)
            Disc(XIcons.watchSynced, sleep, 104.dp)
        }
    }
}

/** A disc's fill and its icon's tint. */
private data class Tone(val container: Color, val icon: Color)

@Composable
private fun Disc(icon: ImageVector, tone: Tone, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(tone.container),
        contentAlignment = Alignment.Center,
    ) {
        XIcon(icon, contentDescription = null, tint = tone.icon, size = size * 0.45f)
    }
}

/** Where the parent is in the tour: one dot per page, the current one wide; TalkBack reads "Page 2 of 6". */
@Composable
private fun PageDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val spoken = stringResource(R.string.intro_page, current + 1, count)
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == current
            Box(
                Modifier
                    .size(width = if (selected) 24.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.onSurface else colors.outlineVariant),
            )
        }
    }
}
