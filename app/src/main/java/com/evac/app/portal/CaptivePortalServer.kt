package com.evac.app.portal

import android.content.Context
import android.util.Log
import com.evac.app.db.AppDatabase
import com.evac.app.db.MessageEntity
import com.evac.app.util.DeviceFingerprint
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID

class CaptivePortalServer(
    private val context: Context,
    port: Int = 8080
) : NanoHTTPD(port) {

    private val TAG = "CaptivePortalServer"
    private val database = AppDatabase.getDatabase(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun serve(session: IHTTPSession): Response {
        return when {
            session.method == Method.GET && session.uri == "/" -> servePage()
            session.method == Method.POST && session.uri == "/sos" -> handleSos(session)
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found"
            )
        }
    }

    private fun servePage(): Response {
        val bulletins = runBlocking {
            database.messageDao().getBulletinsAndAcks().first()
        }

        val bulletinHtml = if (bulletins.isEmpty()) {
            "<p>No bulletins at this time.</p>"
        } else {
            bulletins.joinToString("") { msg ->
                """
                <div class="bulletin">
                    <strong>${msg.type}</strong>
                    <p>${msg.body ?: "No content"}</p>
                </div>
                """.trimIndent()
            }
        }

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>EVAC Emergency Portal</title>
                <style>
                    body { font-family: Arial, sans-serif; background:#1a1a2e;
                           color:white; padding:20px; max-width:600px; margin:auto; }
                    h1   { color:#E94560; }
                    .bulletin { background:#16213e; padding:12px;
                                border-radius:8px; margin-bottom:12px; }
                    input, textarea, select {
                        width:100%; padding:10px; margin:8px 0;
                        background:#16213e; color:white;
                        border:1px solid #E94560; border-radius:4px;
                        box-sizing:border-box; }
                    button { background:#E94560; color:white; padding:12px 24px;
                             border:none; border-radius:4px;
                             font-size:16px; width:100%; cursor:pointer; }
                </style>
            </head>
            <body>
                <h1>🚨 EVAC Emergency Portal</h1>
                <h2>Active Bulletins</h2>
                $bulletinHtml
                <hr>
                <h2>Send SOS</h2>
                <form method="POST" action="/sos">
                    <select name="status">
                        <option value="MEDICAL">🚑 Medical Emergency</option>
                        <option value="TRAPPED">🆘 Trapped</option>
                        <option value="HAZARD">⚠️ Hazard</option>
                        <option value="SAFE">✅ I'm Safe</option>
                    </select>
                    <input type="number" name="people" placeholder="Number of people" min="1" max="10" value="1">
                    <textarea name="note" placeholder="Details (optional)" maxlength="100"></textarea>
                    <button type="submit">SEND SOS</button>
                </form>
            </body>
            </html>
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }

    private fun handleSos(session: IHTTPSession): Response {
        return try {
            val params = mutableMapOf<String, String>()
            session.parseBody(params)
            val formData = session.parameters

            val status  = formData["status"]?.firstOrNull() ?: "MEDICAL"
            val people  = formData["people"]?.firstOrNull()?.toIntOrNull() ?: 1
            val note    = formData["note"]?.firstOrNull()
            val deviceId = DeviceFingerprint.getDeviceId(context)

            val message = MessageEntity(
                id             = UUID.randomUUID().toString(),
                type           = "SOS",
                status         = status,
                deviceId       = "portal-$deviceId",
                timestamp      = System.currentTimeMillis(),
                ttlHours       = 24,
                hopCount       = 0,
                maxHops        = 10,
                lat            = null,
                lng            = null,
                accuracyM      = null,
                peopleCount    = people,
                batteryPct     = null,
                note           = note?.ifEmpty { null },
                phraseKey      = null,
                isVolumeSos    = false,
                hash           = "portal",
                body           = null,
                targetDeviceId = null,
                signature      = null
            )

            scope.launch {
                database.messageDao().insert(message)
                Log.d(TAG, "Portal SOS saved: $status")
            }

            val html = """
                <!DOCTYPE html><html><head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>SOS Sent</title>
                <style>body{font-family:Arial;background:#1a1a2e;color:white;
                padding:20px;text-align:center;}
                h1{color:#388E3C;} a{color:#E94560;}</style>
                </head><body>
                <h1>✅ SOS Sent!</h1>
                <p>Your $status signal has been received.</p>
                <p>Help is being coordinated.</p>
                <a href="/">← Back to portal</a>
                </body></html>
            """.trimIndent()

            newFixedLengthResponse(Response.Status.OK, "text/html", html)
        } catch (e: Exception) {
            Log.e(TAG, "handleSos error: $e")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Error processing SOS"
            )
        }
    }
}