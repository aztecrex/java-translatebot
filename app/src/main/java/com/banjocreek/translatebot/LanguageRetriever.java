package com.banjocreek.translatebot;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonObject;

public final class LanguageRetriever {

    public static Set<String> fetch(final String authToken, final AtomicReference<LanguageRetriever> retriever) {
        while (true) {
            final LanguageRetriever cur = retriever.get();
            if (cur == null) {
                final LanguageRetriever proposed = new LanguageRetriever(authToken);
                if (retriever.compareAndSet(null, proposed))
                    return proposed.get();
            } else
                return cur.get();
        }
    }

    public static Set<String> fetch(final String key, final String authToken,
            final ConcurrentHashMap<String, LanguageRetriever> values) {
        final LanguageRetriever cur = values.get(key);
        if (cur == null)
            return values.putIfAbsent(key, new LanguageRetriever(authToken)).get();
        else
            return cur.get();
    }

    private final String authToken;

    private boolean fired = false;

    private final Object monitor = new Object();

    private Set<String> v;

    private RuntimeException x;

    public LanguageRetriever(final String authToken) {
        this.authToken = authToken;
    }

    public Set<String> get() {

        if (elect()) {
            fetchTheValue();
        }

        synchronized (this.monitor) {
            try {
                while (this.v == null && this.x == null) {
                    this.monitor.wait();
                }
            } catch (final InterruptedException ix) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("interrupted");
            }
            if (this.x != null)
                throw this.x;
            else if (this.v != null)
                return this.v;
            else
                throw new AssertionError("should never get here");
        }
    }

    private boolean elect() {
        synchronized (this.monitor) {
            if (this.fired)
                return false;
            else {
                this.fired = true;
                return true;
            }
        }
    }

    private void fetchTheValue() {
        try {
            final Set<String> result = invoke();
            synchronized (this.monitor) {
                if (result == null) {
                    this.x = new RuntimeException("no result");
                } else {
                    this.v = result;
                }
            }
        } catch (final RuntimeException rtx) {
            synchronized (this.monitor) {
                this.x = rtx;
            }
        }
    }

    private Set<String> invoke() {
        final HashMap<String, String> params = new HashMap<>();
        params.put("key", this.authToken);

        try {
            final URL u = new URL("https://www.googleapis.com/language/translate/v2/languages?" + urlEncodeAll(params));
            System.out.println("URL is " + u);
            final HttpURLConnection connection = (HttpURLConnection) u.openConnection();
            connection.setDoInput(true);
            connection.setRequestMethod("GET");
            final int status = connection.getResponseCode();
            try (InputStream is = connection.getInputStream()) {
                if (status < 200 || status >= 300) {
                    System.err.println("ERROR: Google returned an error: " + status);
                    pipe(is, System.err);
                    throw new RuntimeException("google returned an error status: " + status);
                } else {
                    final JsonObject entity = Json.createReader(is).readObject();
                    return entity.getJsonObject("data")
                            .getJsonArray("languages")
                            .stream()
                            .map(JsonObject.class::cast)
                            .map(o -> o.getString("language"))
                            .collect(Collectors.toSet());
                }
            } finally {
                connection.disconnect();
            }
        } catch (final RuntimeException x) {
            throw x;
        } catch (final Exception x) {
            throw new RuntimeException("http invocation error trying to reach google", x);
        }
    }

    private void pipe(final InputStream is, final OutputStream dest) throws IOException {
        final ByteBuffer buf = ByteBuffer.allocate(1024);
        final ReadableByteChannel ich = Channels.newChannel(is);
        final WritableByteChannel och = Channels.newChannel(dest);
        while (ich.read(buf) >= 0) {
            buf.flip();
            while (buf.hasRemaining()) {
                och.write(buf);
            }
            buf.clear();
        }
    }

    private final String urlEncodeAll(final Map<String, String> params) {
        return params.entrySet().stream().map(this::urlEncodeEntry).collect(Collectors.joining("&"));
    }

    private final String urlEncodeEntry(final Entry<String, String> entry) {
        return urlEncodeProperty(entry.getKey(), entry.getValue());
    }

    private final String urlEncodeProperty(final String name, final String value) {
        try {
            return new StringBuffer().append(name).append("=").append(URLEncoder.encode(value, "UTF-8")).toString();
        } catch (final UnsupportedEncodingException e) {
            throw new RuntimeException("cannot encode value", e);
        }
    }

}
