package com.curro.app.di

import com.curro.app.domain.handler.FunctionHandler
import com.curro.app.handler.CalculateHandler
import com.curro.app.handler.CallContactHandler
import com.curro.app.handler.HelpHandler
import com.curro.app.handler.OpenAppHandler
import com.curro.app.handler.ReadAllUnreadWhatsAppHandler
import com.curro.app.handler.ReadLastWhatsAppHandler
import com.curro.app.handler.TellTimeHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.Multibinds
import dagger.multibindings.StringKey

/**
 * Hilt multibinding module for [FunctionHandler] implementations.
 *
 * `@Multibinds` declares the `Map<String, FunctionHandler>` binding so the
 * graph compiles even when the map is empty (SF-4.1 baseline). Each Phase-4
 * SF appends one `@Binds @IntoMap @StringKey("<action>")` line here;
 * [com.curro.app.domain.handler.HandlerDispatcher] receives the populated map.
 *
 * SF-4.2 (US-026): tell_time
 * SF-4.3 (US-027): open_app
 * SF-4.4 (US-028): calculate
 * SF-4.5 (US-029): help
 * SF-4.7 (US-031): read_last_whatsapp
 * SF-4.8 (US-032): read_all_unread_whatsapp
 * SF-4.10 (US-034): call_contact
 */
@Module
@InstallIn(SingletonComponent::class)
interface HandlerModule {
    @Multibinds
    fun handlerMap(): Map<String, @JvmSuppressWildcards FunctionHandler>

    @Binds
    @IntoMap
    @StringKey("tell_time")
    fun bindTellTimeHandler(impl: TellTimeHandler): FunctionHandler

    @Binds
    @IntoMap
    @StringKey("open_app")
    fun bindOpenAppHandler(impl: OpenAppHandler): FunctionHandler

    @Binds
    @IntoMap
    @StringKey("calculate")
    fun bindCalculateHandler(impl: CalculateHandler): FunctionHandler

    @Binds
    @IntoMap
    @StringKey("help")
    fun bindHelpHandler(impl: HelpHandler): FunctionHandler

    @Binds
    @IntoMap
    @StringKey("read_last_whatsapp")
    fun bindReadLastWhatsAppHandler(impl: ReadLastWhatsAppHandler): FunctionHandler

    @Binds
    @IntoMap
    @StringKey("read_all_unread_whatsapp")
    fun bindReadAllUnreadWhatsAppHandler(impl: ReadAllUnreadWhatsAppHandler): FunctionHandler

    @Binds
    @IntoMap
    @StringKey("call_contact")
    fun bindCallContactHandler(impl: CallContactHandler): FunctionHandler
}
