/*
 * Ajouts Miah — envoi de messages et de fichiers via un bot Telegram personnel.
 * Le téléphone appelle directement l'API Telegram : aucun serveur intermédiaire.
 *
 * This file is part of a modified version of Loop Habit Tracker (GPLv3).
 */
package org.isoron.uhabits.miah

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MiahTelegram {
    private const val TAG = "MiahTelegram"
    private val main = Handler(Looper.getMainLooper())

    class Result(val ok: Boolean, val message: String)

    private fun api(token: String, method: String) = "https://api.telegram.org/bot$token/$method"

    fun sendMessageAsync(context: Context, text: String, onDone: ((Result) -> Unit)? = null) {
        val token = MiahPrefs.botToken(context)
        val chat = MiahPrefs.chatId(context)
        Thread {
            val r = sendMessage(token, chat, text)
            if (!r.ok) Log.w(TAG, "sendMessage: ${r.message}")
            onDone?.let { cb -> main.post { cb(r) } }
        }.start()
    }

    fun sendMessage(token: String, chat: String, text: String): Result {
        if (token.isEmpty() || chat.isEmpty()) return Result(false, "Bot ou chat ID manquant")
        return try {
            val body = "chat_id=" + URLEncoder.encode(chat, "UTF-8") +
                "&text=" + URLEncoder.encode(text, "UTF-8")
            val conn = URL(api(token, "sendMessage")).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            readResult(conn)
        } catch (e: Exception) {
            Result(false, e.message ?: e.toString())
        }
    }

    fun sendDocumentAsync(
        context: Context,
        file: File,
        caption: String,
        onDone: ((Result) -> Unit)? = null
    ) {
        val token = MiahPrefs.botToken(context)
        val chat = MiahPrefs.chatId(context)
        Thread {
            val r = sendDocument(token, chat, file, caption)
            if (!r.ok) Log.w(TAG, "sendDocument: ${r.message}")
            onDone?.let { cb -> main.post { cb(r) } }
        }.start()
    }

    fun sendDocument(token: String, chat: String, file: File, caption: String): Result {
        if (token.isEmpty() || chat.isEmpty()) return Result(false, "Bot ou chat ID manquant")
        val boundary = "----MiahBoundary" + System.currentTimeMillis()
        return try {
            val conn = URL(api(token, "sendDocument")).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            DataOutputStream(conn.outputStream).use { out ->
                fun field(name: String, value: String) {
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n")
                    out.writeBytes("Content-Type: text/plain; charset=UTF-8\r\n\r\n")
                    out.write(value.toByteArray(Charsets.UTF_8))
                    out.writeBytes("\r\n")
                }
                field("chat_id", chat)
                field("caption", caption)
                out.writeBytes("--$boundary\r\n")
                out.writeBytes(
                    "Content-Disposition: form-data; name=\"document\"; filename=\"${file.name}\"\r\n"
                )
                out.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
                file.inputStream().use { it.copyTo(out) }
                out.writeBytes("\r\n--$boundary--\r\n")
            }
            readResult(conn)
        } catch (e: Exception) {
            Result(false, e.message ?: e.toString())
        }
    }

    /**
     * Cherche le chat ID dans les derniers messages reçus par le bot.
     * Il faut d'abord envoyer un message (ex. « bonjour ») au bot depuis Telegram.
     */
    fun findChatIdAsync(token: String, onDone: (Result) -> Unit) {
        Thread {
            val r = try {
                val conn = URL(api(token.trim(), "getUpdates")).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                val json = JSONObject(text)
                if (!json.optBoolean("ok")) {
                    Result(false, json.optString("description", "Token invalide"))
                } else {
                    val arr = json.getJSONArray("result")
                    var chatId: String? = null
                    var name = ""
                    for (i in arr.length() - 1 downTo 0) {
                        val upd = arr.getJSONObject(i)
                        val msg = upd.optJSONObject("message")
                            ?: upd.optJSONObject("channel_post")
                            ?: continue
                        val chat = msg.optJSONObject("chat") ?: continue
                        chatId = chat.get("id").toString()
                        name = chat.optString("first_name", chat.optString("title", ""))
                        break
                    }
                    if (chatId == null) {
                        Result(false, "Aucun message trouvé : envoie d'abord « bonjour » à ton bot dans Telegram, puis réessaie.")
                    } else {
                        Result(true, "$chatId|$name")
                    }
                }
            } catch (e: Exception) {
                Result(false, e.message ?: e.toString())
            }
            main.post { onDone(r) }
        }.start()
    }

    private fun readResult(conn: HttpURLConnection): Result {
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        return try {
            val json = JSONObject(text)
            if (json.optBoolean("ok")) {
                Result(true, "OK")
            } else {
                Result(false, json.optString("description", "Erreur $code"))
            }
        } catch (e: Exception) {
            Result(code in 200..299, "HTTP $code")
        }
    }
}
