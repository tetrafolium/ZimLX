package org.zimmob.zimlx.globalsearch.providers

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.annotation.Keep
import com.android.launcher3.R
import org.zimmob.zimlx.ZimLauncher
import org.zimmob.zimlx.globalsearch.SearchProvider

@Keep
class GoogleSearchProvider(context: Context) : SearchProvider(context) {

    override val name = context.getString(R.string.google)
    override val supportsVoiceSearch = true
    override val supportsAssistant = true
    override val supportsFeed = true
    override val settingsIntent: Intent
        get() = Intent("com.google.android.googlequicksearchbox.TEXT_ASSIST")
            .setPackage(PACKAGE).addFlags(268435456)
    override val isBroadcast: Boolean
        get() = true

    override fun startSearch(callback: (intent: Intent) -> Unit) = callback(Intent().setClassName(PACKAGE, "$PACKAGE.SearchActivity"))

    override fun startVoiceSearch(callback: (intent: Intent) -> Unit) = callback(Intent("android.intent.action.VOICE_ASSIST").setPackage(PACKAGE))

    override fun startAssistant(callback: (intent: Intent) -> Unit) = callback(Intent(Intent.ACTION_VOICE_COMMAND).setPackage(PACKAGE))

    override fun startFeed(callback: (intent: Intent) -> Unit) {
        val launcher = ZimLauncher.getLauncher(context)
        if (launcher.googleNow != null) {
            launcher.googleNow?.showOverlay(true)
        } else {
            callback(Intent(Intent.ACTION_MAIN).setClassName(PACKAGE, "$PACKAGE.SearchActivity"))
        }
    }

    override fun getIcon(): Drawable = context.getDrawable(R.drawable.ic_super_g_color)!!

    override fun getVoiceIcon(): Drawable = context.getDrawable(R.drawable.ic_mic_color)!!

    override fun getAssistantIcon(): Drawable = context.getDrawable(R.drawable.opa_assistant_logo)!!

    companion object {
        private const val PACKAGE = "com.google.android.googlequicksearchbox"
    }
}
