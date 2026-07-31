package com.yourname.litelinkcompanion.commands

import android.content.Context
import android.util.Log
import org.json.JSONObject

private const val TAG = "LiteLink.Cmd"

/** A command handler: receives the shared context, request id and params. */
typealias CommandHandler = (CommandContext, String, JSONObject) -> Unit

/**
 * Central registry that routes desktop commands ({"type":"cmd","cmd":"x",...})
 * to feature modules. Modules self-register in registerAll().
 */
object CommandCenter {

    private val registry = LinkedHashMap<String, CommandHandler>()

    @Volatile
    private var appContext: Context? = null

    /** Application context for code outside command handlers (listeners, watchers). */
    fun getContext(): Context? = appContext

    fun register(name: String, handler: CommandHandler) {
        registry[name] = handler
    }

    fun registerAll(appContext: Context, ctx: CommandContext) {
        appContext.applicationContext.let { this.appContext = it }
        registry.clear()
        BatteryModule.register(appContext)
        SignalModule.register(appContext)
        FindPhoneModule.register(appContext)
        TetherModule.register(appContext)
        SmsArchiveModule.register(appContext)
        MpesaModule.register(appContext)
        UssdModule.register(appContext)
        ApkModule.register(appContext)
        ClipboardModule.register(appContext)
        QuickReplyModule.register(appContext)
        MacroModule.register(appContext)
        FileTransferModule.register()
        FolderSyncModule.register(appContext)
        MediaOffloaderModule.register(appContext)
        WhatsAppBackupModule.register(appContext)
        CameraModule.register(appContext)
        MaintenanceModule.register(appContext)
        SystemModule.register(appContext)
    }

    /** Dispatch an incoming command. Unknown commands get an error result. */
    fun dispatch(ctx: CommandContext, cmd: String, id: String, params: JSONObject) {
        val handler = registry[cmd]
        if (handler == null) {
            Log.w(TAG, "Unknown command: $cmd")
            ctx.fail(id, "unknown command: $cmd")
            return
        }
        try {
            handler(ctx, id, params)
        } catch (e: Exception) {
            Log.e(TAG, "Command $cmd failed", e)
            ctx.fail(id, e.message ?: e.javaClass.simpleName)
        }
    }
}
