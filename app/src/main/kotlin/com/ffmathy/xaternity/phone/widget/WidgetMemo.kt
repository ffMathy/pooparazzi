package com.ffmathy.xaternity.phone.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ffmathy.xaternity.core.model.DiaperKind
import com.ffmathy.xaternity.phone.launch.ReceiptKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The receipt of the last log made on a widget (DESIGN_SPEC §3.3.6, §10.11): row 1 shows "Poop logged at 14:32" with
 * Undo until the next log or [expiresAtMillis]. [previousJson] is the entry before the log, for Undo of "Woke up" and
 * "Sleep cancelled" (which bring the sleep back).
 */
@Serializable
data class WidgetReceipt(
    val kind: ReceiptKind,
    val entryId: String,
    val atMillis: Long,
    val expiresAtMillis: Long,
    val diaperKind: DiaperKind? = null,
    val previousJson: String? = null,
)

/** A short line in row 1 that needs no action: the 2 s guard and dedupe lines, and "Undone" (DESIGN_SPEC §10.7). */
@Serializable
data class WidgetNotice(
    val type: NoticeType,
    /** The time the line names ("Already asleep since 02:05"). */
    val atMillis: Long,
    val expiresAtMillis: Long,
    val diaperKind: DiaperKind? = null,
)

enum class NoticeType { ALREADY_ASLEEP, ALREADY_AWAKE, ALREADY_LOGGED, UNDONE }

/** Everything the widgets remember between taps. Device-local; shared by every placed widget. */
@Serializable
data class WidgetMemo(
    val receipt: WidgetReceipt? = null,
    val notice: WidgetNotice? = null,
    /** Clock time of the last sleep toggle on a widget, for the 2 s double-toggle guard. */
    val lastToggleAtMillis: Long? = null,
)

/** Where [WidgetMemo] lives. An interface so the commands can be tested without SharedPreferences. */
interface WidgetMemoStore {
    val memo: StateFlow<WidgetMemo>
    fun update(transform: (WidgetMemo) -> WidgetMemo): WidgetMemo
}

/** [WidgetMemoStore] in SharedPreferences, mirrored in a [StateFlow] that the widget sessions observe. */
class PrefsWidgetMemoStore private constructor(private val prefs: SharedPreferences) : WidgetMemoStore {
    private val state = MutableStateFlow(read())
    override val memo: StateFlow<WidgetMemo> = state.asStateFlow()

    @Synchronized
    override fun update(transform: (WidgetMemo) -> WidgetMemo): WidgetMemo {
        val next = transform(state.value)
        prefs.edit(commit = true) { putString(KEY, json.encodeToString(WidgetMemo.serializer(), next)) }
        state.value = next
        return next
    }

    private fun read(): WidgetMemo =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(WidgetMemo.serializer(), it) }.getOrNull() } ?: WidgetMemo()

    companion object {
        /** The preferences file; excluded from backup and device transfer (res/xml/data_extraction_rules.xml). */
        internal const val FILE = "widget_memo"
        private const val KEY = "memo"
        private val json = Json { ignoreUnknownKeys = true }

        @Volatile
        private var instance: PrefsWidgetMemoStore? = null

        /** One store per process, so every widget session sees the same flow. */
        fun get(context: Context): PrefsWidgetMemoStore = instance ?: synchronized(this) {
            instance ?: PrefsWidgetMemoStore(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE))
                .also { instance = it }
        }
    }
}

/** A [WidgetMemoStore] in memory (tests, previews). */
class InMemoryWidgetMemoStore(initial: WidgetMemo = WidgetMemo()) : WidgetMemoStore {
    private val state = MutableStateFlow(initial)
    override val memo: StateFlow<WidgetMemo> = state.asStateFlow()

    @Synchronized
    override fun update(transform: (WidgetMemo) -> WidgetMemo): WidgetMemo = transform(state.value).also { state.value = it }
}
