package dev.jdtech.mpv

import android.content.Context
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

class MpvPlayer private constructor() : AutoCloseable {

    companion object {
        init {
            System.loadLibrary("mpv")
            System.loadLibrary("player")
        }

        private val instance = AtomicReference<MpvPlayer?>(null)

        suspend fun create(
            context: Context,
            configure: MpvPlayerConfig.() -> Unit = {},
        ): MpvPlayer = withContext(Dispatchers.IO) {
            val player = MpvPlayer()
            // Atomically replace; mark old as closed so its background close() skips nativeDestroy
            instance.getAndSet(player)?.also { it.closed = true }
            // nativeCreate's safety net handles any leaked native session
            try {
                nativeCreate(context.applicationContext)
                MpvPlayerConfig().apply(configure)
                nativeInit()
                ensureActive()
                player
            } catch (e: Throwable) {
                instance.compareAndSet(player, null)
                try { nativeDestroy() } catch (_: Throwable) {}
                throw e
            }
        }

        // JNI callbacks — called from native event thread

        @JvmStatic
        fun onPropertyChanged(name: String) {
            instance.get()?.propertyChanges?.tryEmit(PropertyChange.None(name))
        }

        @JvmStatic
        fun onPropertyChanged(name: String, value: Boolean) {
            instance.get()?.propertyChanges?.tryEmit(PropertyChange.Flag(name, value))
        }

        @JvmStatic
        fun onPropertyChanged(name: String, value: Long) {
            instance.get()?.propertyChanges?.tryEmit(PropertyChange.Int64(name, value))
        }

        @JvmStatic
        fun onPropertyChanged(name: String, value: Double) {
            instance.get()?.propertyChanges?.tryEmit(PropertyChange.Double(name, value))
        }

        @JvmStatic
        fun onPropertyChanged(name: String, value: String) {
            instance.get()?.propertyChanges?.tryEmit(
                PropertyChange.Str(name, sanitizeString(value)),
            )
        }

        @JvmStatic
        fun onEvent(eventId: Int) {
            val event = MpvEvent.fromId(eventId) ?: return
            instance.get()?.events?.tryEmit(event)
        }

        @JvmStatic
        fun onEndFile(reason: Int) {
            instance.get()?.events?.tryEmit(
                MpvEvent.EndFile(EndFileReason.fromId(reason)),
            )
        }

        @JvmStatic
        fun onLogMessage(prefix: String, level: Int, text: String) {
            val logLevel = LogLevel.fromNative(level) ?: return
            instance.get()?.logMessages?.tryEmit(
                LogMessage(prefix, logLevel, sanitizeString(text).trimEnd()),
            )
        }

        private fun sanitizeString(s: String): String {
            val sb = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c.isHighSurrogate()) {
                    if (i + 1 < s.length && s[i + 1].isLowSurrogate()) {
                        sb.append(c)
                        sb.append(s[i + 1])
                        i += 2
                    } else {
                        sb.append('\uFFFD')
                        i++
                    }
                } else if (c.isLowSurrogate()) {
                    sb.append('\uFFFD')
                    i++
                } else {
                    sb.append(c)
                    i++
                }
            }
            return sb.toString()
        }

        // JNI native declarations — private to avoid internal name mangling

        @JvmStatic private external fun nativeCreate(appctx: Context)
        @JvmStatic private external fun nativeInit()
        @JvmStatic private external fun nativeDestroy()
        @JvmStatic private external fun nativeCommand(cmd: Array<out String>)
        @JvmStatic private external fun nativeSetOptionString(name: String, value: String): Int
        @JvmStatic private external fun nativeAttachSurface(surface: Surface)
        @JvmStatic private external fun nativeDetachSurface()
        @JvmStatic private external fun nativeAttachOsdSurface(surface: Surface)
        @JvmStatic private external fun nativeDetachOsdSurface()
        @JvmStatic private external fun nativeGetPropertyInt(name: String): Int?
        @JvmStatic private external fun nativeGetPropertyDouble(name: String): Double?
        @JvmStatic private external fun nativeGetPropertyBoolean(name: String): Boolean?
        @JvmStatic private external fun nativeGetPropertyString(name: String): String?
        @JvmStatic private external fun nativeSetPropertyInt(name: String, value: Int)
        @JvmStatic private external fun nativeSetPropertyDouble(name: String, value: Double)
        @JvmStatic private external fun nativeSetPropertyBoolean(name: String, value: Boolean)
        @JvmStatic private external fun nativeSetPropertyString(name: String, value: String)
        @JvmStatic private external fun nativeObserveProperty(name: String, format: Int)

        internal fun setOptionString(name: String, value: String): Int = nativeSetOptionString(name, value)
    }

    private val events = MutableSharedFlow<MpvEvent>(extraBufferCapacity = 64)
    private val propertyChanges = MutableSharedFlow<PropertyChange>(extraBufferCapacity = 64)
    private val logMessages = MutableSharedFlow<LogMessage>(extraBufferCapacity = 64)

    val eventFlow: SharedFlow<MpvEvent> = events.asSharedFlow()
    val propertyFlow: SharedFlow<PropertyChange> = propertyChanges.asSharedFlow()
    val logFlow: SharedFlow<LogMessage> = logMessages.asSharedFlow()

    // Commands

    suspend fun command(vararg args: String) {
        checkNotClosed()
        withContext(Dispatchers.IO) { nativeCommand(args) }
    }

    // Surface — not suspend, called from SurfaceHolder.Callback

    fun attachSurface(surface: Surface) {
        checkNotClosed()
        nativeAttachSurface(surface)
    }

    fun detachSurface() {
        checkNotClosed()
        nativeDetachSurface()
    }

    /** OSD/subtitle plane for `vo=mediacodec`; attach before selecting the VO. */
    fun attachOsdSurface(surface: Surface) {
        checkNotClosed()
        nativeAttachOsdSurface(surface)
    }

    fun detachOsdSurface() {
        checkNotClosed()
        nativeDetachOsdSurface()
    }

    // Property getters

    suspend fun getInt(name: String): Int? {
        checkNotClosed()
        return withContext(Dispatchers.IO) { nativeGetPropertyInt(name) }
    }

    suspend fun getDouble(name: String): Double? {
        checkNotClosed()
        return withContext(Dispatchers.IO) { nativeGetPropertyDouble(name) }
    }

    suspend fun getFlag(name: String): Boolean? {
        checkNotClosed()
        return withContext(Dispatchers.IO) { nativeGetPropertyBoolean(name) }
    }

    suspend fun getString(name: String): String? {
        checkNotClosed()
        return withContext(Dispatchers.IO) { nativeGetPropertyString(name) }
    }

    // Property setters

    suspend fun setProperty(name: String, value: Int) {
        checkNotClosed()
        withContext(Dispatchers.IO) { nativeSetPropertyInt(name, value) }
    }

    suspend fun setProperty(name: String, value: Double) {
        checkNotClosed()
        withContext(Dispatchers.IO) { nativeSetPropertyDouble(name, value) }
    }

    suspend fun setProperty(name: String, value: Boolean) {
        checkNotClosed()
        withContext(Dispatchers.IO) { nativeSetPropertyBoolean(name, value) }
    }

    suspend fun setProperty(name: String, value: String) {
        checkNotClosed()
        withContext(Dispatchers.IO) { nativeSetPropertyString(name, value) }
    }

    // Property observation

    fun observeProperty(name: String, format: PropertyFormat): Flow<PropertyChange> {
        checkNotClosed()
        nativeObserveProperty(name, format.nativeValue)
        return propertyFlow.filter { it.name == name }
    }

    fun observeFlag(name: String): Flow<Boolean> {
        checkNotClosed()
        nativeObserveProperty(name, PropertyFormat.Flag.nativeValue)
        return propertyFlow
            .filterIsInstance<PropertyChange.Flag>()
            .filter { it.name == name }
            .map { it.value }
    }

    fun observeInt(name: String): Flow<Long> {
        checkNotClosed()
        nativeObserveProperty(name, PropertyFormat.Int64.nativeValue)
        return propertyFlow
            .filterIsInstance<PropertyChange.Int64>()
            .filter { it.name == name }
            .map { it.value }
    }

    fun observeDouble(name: String): Flow<Double> {
        checkNotClosed()
        nativeObserveProperty(name, PropertyFormat.Double.nativeValue)
        return propertyFlow
            .filterIsInstance<PropertyChange.Double>()
            .filter { it.name == name }
            .map { it.value }
    }

    fun observeString(name: String): Flow<String> {
        checkNotClosed()
        nativeObserveProperty(name, PropertyFormat.String.nativeValue)
        return propertyFlow
            .filterIsInstance<PropertyChange.Str>()
            .filter { it.name == name }
            .map { it.value }
    }

    // Lifecycle

    @Volatile
    private var closed = false

    override fun close() {
        if (closed) return
        closed = true
        // Only destroy native if we're still the active player.
        // If create() already replaced us, nativeCreate's safety net handles native cleanup.
        if (instance.compareAndSet(this, null)) {
            nativeDestroy()
        }
    }

    private fun checkNotClosed() {
        check(!closed) { "MpvPlayer has been closed" }
    }
}
