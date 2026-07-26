package com.ksp.agent.chat.tts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal Edge Read-Aloud client with up-to-date {@code Sec-MS-GEC} auth
 * (same algorithm as <a href="https://github.com/rany2/edge-tts">edge-tts</a>).
 * Avoids the stale Chromium/GEC constants in older {@code tts-edge-java} releases that cause 403.
 */
public final class EdgeTtsClient {

    private static final Logger log = LoggerFactory.getLogger(EdgeTtsClient.class);

    private static final String TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    private static final String CHROMIUM_FULL_VERSION = "143.0.3650.75";
    private static final String CHROMIUM_MAJOR = CHROMIUM_FULL_VERSION.split("\\.", 2)[0];
    private static final String SEC_MS_GEC_VERSION = "1-" + CHROMIUM_FULL_VERSION;
    private static final long WIN_EPOCH = 11_644_473_600L;
    private static final String WSS_BASE =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
                    + "?TrustedClientToken=" + TRUSTED_CLIENT_TOKEN;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/" + CHROMIUM_MAJOR + ".0.0.0 Safari/537.36 Edg/" + CHROMIUM_MAJOR + ".0.0.0";

    private static volatile double clockSkewSeconds = 0.0;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public byte[] synthesizeMp3(String voice, String text) {
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return synthesizeOnce(voice, text);
            } catch (Exception e) {
                last = e;
                String msg = e.getMessage() == null ? "" : e.getMessage();
                if (attempt == 0 && (msg.contains("403") || msg.contains("Forbidden"))) {
                    adjustClockSkewFromBing();
                    continue;
                }
                break;
            }
        }
        throw new IllegalStateException(
                "Edge TTS failed: " + (last == null ? "unknown" : last.getMessage()), last);
    }

    private byte[] synthesizeOnce(String voice, String text) throws Exception {
        String cleaned = escapeXml(removeIncompatible(text));
        if (cleaned.isBlank()) {
            return new byte[0];
        }

        String url = WSS_BASE
                + "&ConnectionId=" + connectId()
                + "&Sec-MS-GEC=" + generateSecMsGec()
                + "&Sec-MS-GEC-Version=" + SEC_MS_GEC_VERSION;

        CompletableFuture<byte[]> done = new CompletableFuture<>();
        StringBuilder textBuf = new StringBuilder();
        java.io.ByteArrayOutputStream audio = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream binaryBuf = new java.io.ByteArrayOutputStream();
        AtomicReference<WebSocket> socketRef = new AtomicReference<>();

        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                socketRef.set(webSocket);
                webSocket.request(1);
                String config = "X-Timestamp:" + jsDate() + "\r\n"
                        + "Content-Type:application/json; charset=utf-8\r\n"
                        + "Path:speech.config\r\n\r\n"
                        + "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{"
                        + "\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"true\"},"
                        + "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n";
                webSocket.sendText(config, true);

                String ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>"
                        + "<voice name='" + voice + "'>"
                        + "<prosody pitch='+0Hz' rate='+0%' volume='+0%'>"
                        + cleaned
                        + "</prosody></voice></speak>";
                String ssmlMsg = "X-RequestId:" + connectId() + "\r\n"
                        + "Content-Type:application/ssml+xml\r\n"
                        + "X-Timestamp:" + jsDate() + "Z\r\n"
                        + "Path:ssml\r\n\r\n"
                        + ssml;
                webSocket.sendText(ssmlMsg, true);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                textBuf.append(data);
                if (last) {
                    String msg = textBuf.toString();
                    textBuf.setLength(0);
                    if (msg.contains("Path:turn.end")) {
                        done.complete(audio.toByteArray());
                        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
                    }
                }
                webSocket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                byte[] chunk = new byte[data.remaining()];
                data.get(chunk);
                try {
                    binaryBuf.write(chunk);
                } catch (Exception e) {
                    done.completeExceptionally(e);
                    return null;
                }
                if (last) {
                    byte[] bytes = binaryBuf.toByteArray();
                    binaryBuf.reset();
                    appendAudioFrame(bytes, audio);
                }
                webSocket.request(1);
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                done.completeExceptionally(error);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                if (!done.isDone()) {
                    if (audio.size() > 0) {
                        done.complete(audio.toByteArray());
                    } else {
                        done.completeExceptionally(new IllegalStateException(
                                "WebSocket closed: " + statusCode + " " + reason));
                    }
                }
                return null;
            }
        };

        httpClient.newWebSocketBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "MUID=" + muid())
                .buildAsync(URI.create(url), listener)
                .orTimeout(15, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    done.completeExceptionally(ex);
                    return null;
                });

        try {
            return done.orTimeout(45, TimeUnit.SECONDS).join();
        } catch (Exception e) {
            WebSocket ws = socketRef.get();
            if (ws != null) {
                try {
                    ws.abort();
                } catch (Exception ignored) {
                    // ignore
                }
            }
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw cause instanceof Exception ? (Exception) cause : e;
        }
    }

    /**
     * Edge binary frames: 2-byte big-endian header length, then headers, then audio.
     * Matches edge-tts: audio payload starts at {@code headerLength + 2}.
     */
    private static void appendAudioFrame(byte[] bytes, java.io.ByteArrayOutputStream audio) {
        if (bytes.length < 2) {
            return;
        }
        int headerLength = ((bytes[0] & 0xff) << 8) | (bytes[1] & 0xff);
        if (headerLength > bytes.length) {
            return;
        }
        // Headers occupy bytes[0 .. headerLength); Path is usually after the length prefix.
        String headers = new String(bytes, 0, Math.min(headerLength, bytes.length), StandardCharsets.UTF_8);
        if (!headers.contains("Path:audio") || !headers.contains("audio/mpeg")) {
            return;
        }
        int dataStart = headerLength + 2;
        if (dataStart < bytes.length) {
            audio.write(bytes, dataStart, bytes.length - dataStart);
        }
    }

    private void adjustClockSkewFromBing() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.bing.com"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<Void> res = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            String date = res.headers().firstValue("Date").orElse(null);
            if (date != null) {
                Instant server = DateTimeFormatter.RFC_1123_DATE_TIME.parse(date, Instant::from);
                double skew = server.getEpochSecond() + server.getNano() / 1e9
                        - (Instant.now().getEpochSecond() + Instant.now().getNano() / 1e9);
                clockSkewSeconds += skew;
                log.info("Adjusted Edge TTS clock skew by {}s after 403", String.format(Locale.US, "%.3f", skew));
            }
        } catch (Exception e) {
            log.warn("Could not adjust Edge TTS clock skew: {}", e.getMessage());
        }
    }

    static String generateSecMsGec() throws Exception {
        double ticks = Instant.now().getEpochSecond() + Instant.now().getNano() / 1e9 + clockSkewSeconds;
        ticks += WIN_EPOCH;
        ticks -= ticks % 300;
        ticks *= 1e9 / 100; // 100-ns intervals
        String strToHash = String.format(Locale.US, "%.0f", ticks) + TRUSTED_CLIENT_TOKEN;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(strToHash.getBytes(StandardCharsets.US_ASCII));
        return HexFormat.of().withUpperCase().formatHex(hash);
    }

    private static String connectId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String muid() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }

    private static String jsDate() {
        return DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
    }

    private static String removeIncompatible(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int code = c;
            if ((code >= 0 && code <= 8) || (code >= 11 && code <= 12) || (code >= 14 && code <= 31)) {
                b.append(' ');
            } else {
                b.append(c);
            }
        }
        return b.toString().trim();
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
