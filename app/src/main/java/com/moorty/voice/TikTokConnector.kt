package com.moorty.voice

import android.content.Context
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** TikTok LIVE adapter for the community TikTokLiveJava connector (unofficial, public LIVE rooms). */
class TikTokConnector(
    private val context: Context,
    private val username: String,
    private val normalSound: () -> String?,
    private val subscriberSound: () -> String?,
    private val onStatus: (String) -> Unit
) {
    private val stopped = AtomicBoolean(false)
    @Volatile private var client: Any? = null
    private var worker: Thread? = null
    private val lastEvents = HashMap<String, Long>()

    fun start() {
        if (worker?.isAlive == true) return
        stopped.set(false)
        worker = thread(name = "Moorty-TikTok-$username", isDaemon = true) {
            try {
                onStatus("TikTok: conectando a @$username…")
                val klass = findTikTokClass() ?: throw IllegalStateException("No se encontró TikTokLiveJava en la aplicación. Revisa que Gradle haya descargado la dependencia.")
                var builder: Any = klass.getMethod("newClient", String::class.java).invoke(null, username.removePrefix("@"))
                hook(builder, "onConnected") { onStatus("TikTok conectado a @$username") }
                hook(builder, "onError") { args -> onStatus("TikTok: ${eventText(args.lastOrNull(), "exception", "message")}") }
                hook(builder, "onComment") { args ->
                    val e = args.lastOrNull(); val user = eventText(e, "user", "name", "profileName"); val msg = eventText(e, "text", "comment")
                    if (msg.isNotBlank() && accept("comment:$user:$msg", 2500)) speak("$user dice: ${msg.take(220)}")
                }
                hook(builder, "onGift") { args ->
                    val e = args.lastOrNull(); val user = eventText(e, "user", "name", "profileName"); val gift = eventText(e, "gift", "name")
                    if (accept("gift:$user:$gift", 1500)) speak("¡$user envió $gift! ¡Gracias por el apoyo!")
                }
                hook(builder, "onFollow") { args -> val e=args.lastOrNull(); val user=eventText(e,"user","name","profileName"); if (accept("follow:$user", 30_000)) speak("¡$user empezó a seguir el canal! Muchas gracias.") }
                hook(builder, "onShare") { args -> val e=args.lastOrNull(); val user=eventText(e,"user","name","profileName"); if (accept("share:$user", 10_000)) speak("¡Gracias $user por compartir el directo!") }
                hook(builder, "onJoin") { args ->
                    val e=args.lastOrNull(); val user=eventText(e,"user","name","profileName")
                    // Never guess subscriber state. The subscriber sound is used only when the event exposes an explicit true value.
                    val isSub = explicitSubscriber(e)
                    if (user.isNotBlank() && accept("join:$user", 45_000)) {
                        val sound = if (isSub == true) subscriberSound() else normalSound()
                        VoiceService.speak(context, "¡$user se unió al directo!", 1f, 1f, sound)
                    }
                }
                hook(builder, "onLike") { args -> val e=args.lastOrNull(); val user=eventText(e,"user","name","profileName"); val count=eventText(e,"count","likeCount","total"); if (accept("like:$user", 5000)) speak(if(count.isBlank()) "¡Gracias $user por los likes!" else "¡Gracias $user por enviar $count likes!") }
                hook(builder, "onSubscribe") { args -> val e=args.lastOrNull(); val user=eventText(e,"user","name","profileName"); speak("¡$user se suscribió al canal! Muchas gracias.", subscriberSound()) }
                val connect = builder.javaClass.methods.firstOrNull { it.name == "buildAndConnect" && it.parameterCount == 0 }
                    ?: throw IllegalStateException("La versión del conector no expone buildAndConnect().")
                client = connect.invoke(builder)
                onStatus("TikTok: conector iniciado para @$username. Esperando eventos…")
                while (!stopped.get()) Thread.sleep(1000)
            } catch (e: InterruptedException) {
                // requested stop
            } catch (e: Exception) {
                onStatus("TikTok no pudo conectar: ${e.cause?.message ?: e.message ?: "error desconocido"}")
            }
        }
    }

    fun stop() {
        stopped.set(true)
        try { client?.javaClass?.methods?.firstOrNull { it.name in listOf("disconnect", "stop", "close") && it.parameterCount == 0 }?.invoke(client) } catch (_: Exception) { }
        worker?.interrupt(); worker = null; client = null; onStatus("TikTok desconectado")
    }

    private fun findTikTokClass(): Class<*>? {
        val names = listOf("com.github.jwdeveloper.tiktoklive.TikTokLive", "com.github.jwdeveloper.tiktoklive.client.TikTokLive", "com.github.jwdeveloper.tiktoklive.api.TikTokLive")
        for (name in names) try { return Class.forName(name) } catch (_: Exception) { }
        // Fall back to a dex scan so minor package refactors don't break the adapter.
        try {
            val dexClass = Class.forName("dalvik.system.DexFile")
            val dex = dexClass.getConstructor(String::class.java).newInstance(context.applicationInfo.sourceDir)
            val entries = dexClass.getMethod("entries").invoke(dex) as java.util.Enumeration<*>
            while (entries.hasMoreElements()) {
                val name = entries.nextElement() as? String ?: continue
                if (name.endsWith(".TikTokLive") && name.contains("tiktoklive", true)) try { return Class.forName(name) } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
        return null
    }

    private fun hook(builder: Any, methodName: String, callback: (Array<out Any?>) -> Unit) {
        val method = builder.javaClass.methods.firstOrNull { it.name == methodName && it.parameterCount == 1 } ?: return
        val iface = method.parameterTypes[0]
        if (!iface.isInterface) return
        val proxy = Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { proxyInstance, methodCalled, args ->
            // Java proxies can receive Object methods too. Returning null for hashCode()
            // causes: "Expected to unbox int primitive type but was returned null".
            when {
                methodCalled.declaringClass == Any::class.java && methodCalled.name == "hashCode" ->
                    System.identityHashCode(proxyInstance)
                methodCalled.declaringClass == Any::class.java && methodCalled.name == "equals" ->
                    proxyInstance === args?.firstOrNull()
                methodCalled.declaringClass == Any::class.java && methodCalled.name == "toString" ->
                    "MoortyTikTokCallback(${iface.name})"
                methodCalled.name in listOf("accept", "invoke", "call") -> {
                    callback(args ?: emptyArray())
                    null
                }
                else -> primitiveDefault(methodCalled.returnType)
            }
        }
        method.invoke(builder, proxy)
    }

    private fun primitiveDefault(type: Class<*>): Any? {
        if (!type.isPrimitive) return null
        return when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Character.TYPE -> 0.toChar()
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Void.TYPE -> null
            else -> null
        }
    }

    private fun eventText(root: Any?, vararg path: String): String {
        var current: Any? = root
        for (part in path) {
            if (current == null) return ""
            val getterNames = listOf("get" + part.replaceFirstChar { it.uppercase() }, "is" + part.replaceFirstChar { it.uppercase() }, part)
            current = try { current.javaClass.methods.firstOrNull { it.name in getterNames && it.parameterCount == 0 }?.invoke(current) } catch (_: Exception) { null }
        }
        return current?.toString()?.takeIf { it != "null" } ?: ""
    }

    private fun explicitSubscriber(event: Any?): Boolean? {
        for (root in listOf(event, try { event?.javaClass?.methods?.firstOrNull { it.name == "getUser" && it.parameterCount == 0 }?.invoke(event) } catch (_: Exception) { null })) {
            if (root == null) continue
            for (name in listOf("isSubscriber", "getSubscriber", "isSub", "getIsSubscriber")) {
                try { val value = root.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }?.invoke(root); if (value is Boolean) return value } catch (_: Exception) { }
            }
        }
        return null
    }
    private fun accept(key: String, cooldown: Long): Boolean = synchronized(lastEvents) {
        val now = System.currentTimeMillis(); val old = lastEvents[key] ?: 0L
        if (now - old < cooldown) false else { lastEvents[key] = now; true }
    }
    private fun speak(text: String, sound: String? = null) = VoiceService.speak(context, text, 1f, 1f, sound)
}
