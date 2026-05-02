package com.chartlens.overlay

import android.graphics.Color
import com.chartlens.mediaprojection.CaptureService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Native end-to-end "analyze + draw" runner. Runs entirely on a background
 * thread without depending on the React Native JS thread, which is necessary
 * on aggressive OEM ROMs (Realme, OnePlus, Xiaomi, …) that throttle
 * background apps' JS work even when a foreground service is running.
 *
 * Inputs come from JS once. From there: capture the latest frame, POST to
 * Gemini, parse, filter, and trigger the highlight overlay directly on the
 * main thread. The promise is resolved with a summary the JS side stores in
 * history and uses to update the bubble badge.
 */
object AnalyzeRunner {

  data class Args(
    val apiKey: String,
    val model: String,
    val patternId: String,
    val patternName: String,
    val patternDef: String,
    val color: String,
    val minConfidence: Double,
    val maxBoxes: Int,
    val autoDismissMs: Long,
  )

  data class Match(
    val idx: Int,
    val confidence: Float,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val note: String?,
  )

  data class Result(
    val matches: List<Match>,
    val frameWidth: Int,
    val frameHeight: Int,
    val gotChart: Boolean,
    val rawText: String,
    val durationMs: Long,
    val captureMs: Long,
    val geminiMs: Long,
  )

  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()

  /** Throws on hard errors; returns null analysis if Gemini said no_chart. */
  fun run(args: Args): Result {
    val totalStart = System.currentTimeMillis()
    val service = CaptureService.instance
      ?: throw RuntimeException("Capture service not running — tap broker on Home to restart")

    if (!service.ensureDisplay()) {
      throw RuntimeException("Capture display unavailable — tap broker on Home to restart")
    }

    // 1. Capture
    val captureStart = System.currentTimeMillis()
    var frame: CaptureService.CaptureResult? = service.captureLatest()
    if (frame == null) {
      val newCount = service.waitForNewFrame(service.currentFrameCount(), 3000L)
      if (newCount < 0) {
        throw RuntimeException("No frame within 3s — interact with the chart and tap again")
      }
      var attempts = 0
      while (attempts < 5 && frame == null) {
        frame = service.captureLatest()
        if (frame == null) {
          try { Thread.sleep(60) } catch (_: InterruptedException) {}
        }
        attempts++
      }
    }
    val capturedFrame = frame ?: throw RuntimeException("Frame arrived but acquire failed")
    val captureMs = System.currentTimeMillis() - captureStart

    // 2. Build prompts
    val systemPrompt = buildSystemPrompt(capturedFrame.width, capturedFrame.height)
    val userPrompt = buildUserPrompt(args.patternName, args.patternDef, args.minConfidence)

    // 3. POST to Gemini
    val geminiStart = System.currentTimeMillis()
    val responseText = postToGemini(args.apiKey, args.model, systemPrompt, userPrompt, capturedFrame.base64)
    val geminiMs = System.currentTimeMillis() - geminiStart

    // 4. Parse outer envelope → inner JSON text → matches
    val innerText = extractCandidateText(responseText)
    val parsed = parseInnerJson(innerText)
    val (gotChart, allMatches, parsedW, parsedH) = parsed

    // 5. Filter by confidence, scale to frame, clamp, cap
    val sx = if (parsedW > 0) capturedFrame.width.toFloat() / parsedW else 1f
    val sy = if (parsedH > 0) capturedFrame.height.toFloat() / parsedH else 1f

    val filtered = allMatches
      .filter { it.confidence >= args.minConfidence }
      .map { m ->
        m.copy(
          x = m.x * sx,
          y = m.y * sy,
          w = m.w * sx,
          h = m.h * sy,
        )
      }
      .mapNotNull { clampToFrame(it, capturedFrame.width, capturedFrame.height) }
      .take(args.maxBoxes)

    return Result(
      matches = filtered,
      frameWidth = capturedFrame.width,
      frameHeight = capturedFrame.height,
      gotChart = gotChart,
      rawText = innerText,
      durationMs = System.currentTimeMillis() - totalStart,
      captureMs = captureMs,
      geminiMs = geminiMs,
    )
  }

  private fun buildSystemPrompt(w: Int, h: Int): String = """
    You are a candlestick chart analyst. The image is a screenshot of a stock trading app. The user wants every candle that matches a specific pattern.
    Coordinate system: top-left origin, pixel units. The image dimensions are $w x $h. Echo them as imageWidth and imageHeight in your response.
    IGNORE any small circular floating button overlay in the image (it is a UI element from the analysis app, not part of the chart). Do not include it in any bounding box.
    For each matching candle (or candle group, when the pattern requires multiple candles like Engulfing or Three Soldiers), return a tight axis-aligned bounding box {x, y, w, h} that encloses the relevant candle bodies and wicks. Boxes must stay strictly inside the chart area — do not include axis labels, headers, or sidebars.
    If the pattern is multi-candle, return ONE box around the full group, and set idx to the index of the LAST candle in the group.
    Return ONLY valid JSON in this exact schema, no markdown fences, no prose:
    {"imageWidth": <int>, "imageHeight": <int>, "matches": [{"idx": <int>, "pattern": "<snake_case>", "confidence": <0..1>, "bbox": {"x": <int>, "y": <int>, "w": <int>, "h": <int>}, "note": "<optional short string>"}]}
    If you cannot identify a chart in the image, return {"imageWidth": <int>, "imageHeight": <int>, "matches": [], "error": "no_chart_detected"}.
  """.trimIndent()

  private fun buildUserPrompt(name: String, def: String, minConf: Double): String =
    "Find every **$name** pattern in this candlestick chart. Pattern definition: $def Only include matches you are at least ${"%.2f".format(minConf)} confident about. Return JSON per the schema in the system instructions."

  private fun postToGemini(
    apiKey: String,
    model: String,
    systemPrompt: String,
    userPrompt: String,
    imageBase64: String,
  ): String {
    val urlStr = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

    val systemInstr = JSONObject().apply {
      put("parts", JSONArray().apply {
        put(JSONObject().apply { put("text", systemPrompt) })
      })
    }
    val parts = JSONArray()
      .put(JSONObject().apply { put("text", userPrompt) })
      .put(JSONObject().apply {
        put("inline_data", JSONObject().apply {
          put("mime_type", "image/jpeg")
          put("data", imageBase64)
        })
      })
    val content = JSONObject().apply {
      put("role", "user")
      put("parts", parts)
    }
    val body = JSONObject().apply {
      put("systemInstruction", systemInstr)
      put("contents", JSONArray().put(content))
    }

    val mediaType = "application/json".toMediaType()
    val req = Request.Builder()
      .url(urlStr)
      .post(body.toString().toRequestBody(mediaType))
      .build()

    var attempt = 0
    val maxAttempts = 4
    var lastErr: Exception? = null
    while (attempt < maxAttempts) {
      try {
        client.newCall(req).execute().use { res ->
          val text = res.body?.string() ?: ""
          if (res.isSuccessful) return text
          val status = res.code
          android.util.Log.w("ChartLens", "gemini http $status: ${text.take(200)}")
          if (status == 429 && attempt < maxAttempts - 1) {
            Thread.sleep(minOf(4000L, 1000L * (1 shl attempt)))
            attempt++
            return@use
          }
          if (status == 401 || status == 403) {
            throw RuntimeException("Invalid Gemini API key")
          }
          if (status == 400) {
            throw RuntimeException("Bad request: ${text.take(160)}")
          }
          throw RuntimeException("HTTP $status: ${text.take(160)}")
        }
        // 429 path falls through to retry
      } catch (e: java.io.IOException) {
        lastErr = e
        android.util.Log.w("ChartLens", "gemini network error attempt=$attempt: ${e.message}")
        if (attempt < maxAttempts - 1) {
          try {
            Thread.sleep(minOf(3000L, 600L * (1 shl attempt)))
          } catch (_: InterruptedException) {}
          attempt++
          continue
        }
        throw RuntimeException("Network error: ${e.message}")
      }
      attempt++
    }
    throw RuntimeException("Gemini exhausted retries: ${lastErr?.message ?: "unknown"}")
  }

  private fun extractCandidateText(envelope: String): String {
    return try {
      val obj = JSONObject(envelope)
      val candidate = obj.optJSONArray("candidates")?.optJSONObject(0)
      val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
      val sb = StringBuilder()
      if (parts != null) {
        for (i in 0 until parts.length()) {
          val p = parts.optJSONObject(i)
          if (p != null && p.has("text")) sb.append(p.optString("text", ""))
        }
      }
      stripFences(sb.toString())
    } catch (_: Exception) {
      ""
    }
  }

  private fun stripFences(s: String): String {
    return s.trim()
      .removePrefix("```json").removePrefix("```")
      .removeSuffix("```")
      .trim()
  }

  private data class ParsedInner(
    val gotChart: Boolean,
    val matches: List<Match>,
    val width: Int,
    val height: Int,
  )

  private fun parseInnerJson(text: String): ParsedInner {
    if (text.isBlank()) return ParsedInner(false, emptyList(), 0, 0)
    return try {
      val obj = JSONObject(text)
      val w = obj.optInt("imageWidth", 0)
      val h = obj.optInt("imageHeight", 0)
      val err = obj.optString("error", "")
      if (err == "no_chart_detected") return ParsedInner(false, emptyList(), w, h)
      val arr = obj.optJSONArray("matches") ?: return ParsedInner(true, emptyList(), w, h)
      val out = mutableListOf<Match>()
      for (i in 0 until arr.length()) {
        val m = arr.optJSONObject(i) ?: continue
        val bbox = m.optJSONObject("bbox") ?: continue
        val idx = m.optInt("idx", -1)
        if (idx < 0) continue
        val conf = m.optDouble("confidence", 0.0).toFloat()
        val bx = bbox.optDouble("x", Double.NaN)
        val by = bbox.optDouble("y", Double.NaN)
        val bw = bbox.optDouble("w", Double.NaN)
        val bh = bbox.optDouble("h", Double.NaN)
        if (bx.isNaN() || by.isNaN() || bw.isNaN() || bh.isNaN()) continue
        if (bw <= 0.0 || bh <= 0.0) continue
        val note = if (m.has("note") && !m.isNull("note")) m.optString("note") else null
        out += Match(idx, conf, bx.toFloat(), by.toFloat(), bw.toFloat(), bh.toFloat(), note)
      }
      ParsedInner(true, out, w, h)
    } catch (e: Exception) {
      android.util.Log.w("ChartLens", "parseInnerJson failed: ${e.message}")
      ParsedInner(false, emptyList(), 0, 0)
    }
  }

  private fun clampToFrame(m: Match, fw: Int, fh: Int): Match? {
    val x1 = maxOf(0f, m.x)
    val y1 = maxOf(0f, m.y)
    val x2 = minOf(fw.toFloat(), m.x + m.w)
    val y2 = minOf(fh.toFloat(), m.y + m.h)
    val w = x2 - x1
    val h = y2 - y1
    if (w <= 1f || h <= 1f) return null
    return m.copy(x = x1, y = y1, w = w, h = h)
  }

  fun parseColor(hex: String): Int = try { Color.parseColor(hex) } catch (_: Exception) { Color.parseColor("#5B6CFF") }
}
