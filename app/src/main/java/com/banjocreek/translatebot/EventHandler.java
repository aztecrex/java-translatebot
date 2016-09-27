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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;

public class EventHandler {

    private static <K, V> Function<Map<K, V>, Optional<V>> field(final K key) {
        return m -> Optional.ofNullable(m.get(key));
    }

    private final AtomicReference<DBValueRetriever> botToken = new AtomicReference<>();
    // TODO make this a map per team and create a configuration command
    private final AtomicReference<DBValueRetriever> googleToken = new AtomicReference<>();

    private final ConcurrentHashMap<String, DBValueRetriever> users = new ConcurrentHashMap<>();

    private final AtomicReference<DBValueRetriever> verificationToken = new AtomicReference<>();
    private static final String TableName = "TranslateSlack";
    private static final AmazonDynamoDBClient ddb = new AmazonDynamoDBClient();

    public Map<String, String> handle(final Map<String, Object> envelope) {

        System.out.println("Entering handler");

        final Optional<Object> maybeToken = field("token").apply(envelope).filter(vtoken()::equals);
        if (!maybeToken.isPresent())
            return Collections.emptyMap();

        System.out.println("Token check passed");

        final Optional<String> maybeEnvelopeType = field("type").apply(envelope).map(String.class::cast);
        if (!maybeEnvelopeType.isPresent())
            return Collections.emptyMap();

        System.out.println("Have envelope type: " + maybeEnvelopeType.get());

        if (some(maybeEnvelopeType, "url_verification")) {
            final Optional<String> maybeChallenge = field("challenge").apply(envelope).map(String.class::cast);
            return Collections.singletonMap("challenge", maybeChallenge.orElse("not challenged"));
        }

        System.out.println("Not a URL verification");

        final Optional<Map<String, String>> maybeEvent = field("event").apply(envelope).map(Map.class::cast);

        System.out.println("Might have an event: " + maybeEvent.isPresent());

        maybeEvent.ifPresent(ev -> {
            System.out.println("Event is: " + ev);
            switch (ev.get("type")) {
            case "message":
                System.out.println("Type is Message, dispatching...");
                handleMessage(ev);
                break;
            }
        });

        return Collections.emptyMap();
    }

    private String btoken() {
        return DBValueRetriever.fetch("global:bottoken", this.botToken);
    }

    private Optional<String> fetchUsername(final String userId) {
        final HashMap<String, String> params = new HashMap<>();
        params.put("token", btoken());
        params.put("user", userId);

        try {
            final URL u = new URL("https://slack.com/api/users.info?" + urlEncodeAll(params));
            System.out.println("URL is " + u);
            final HttpURLConnection connection = (HttpURLConnection) u.openConnection();
            connection.setDoInput(true);
            connection.setRequestMethod("POST");
            try (InputStream is = connection.getInputStream()) {
                final int response = connection.getResponseCode();
                if (response < 200 || response >= 300) {
                    System.err.println("ERROR: slack returned an error: " + response);
                    pipe(is, System.err);
                } else {
                    final JsonObject respBody = Json.createReader(is).readObject();
                    if (respBody.containsKey("user")) {
                        final JsonObject user = respBody.getJsonObject("user");
                        if (user.containsKey("name"))
                            return Optional.of(user.getString("name"));
                    }
                    System.err.println("ERROR: cannot find user name in Slack API response: " + respBody);
                }
                return Optional.empty();
            }
        } catch (final Exception x) {
            System.err.println("ERROR: Failure trying to invoke slack API");
            x.printStackTrace(System.err);
            return Optional.empty();
        }

    }

    private String gtoken() {
        return DBValueRetriever.fetch("global:googletoken", this.googleToken);
    }

    private void handleMessage(final Map<String, String> ev) {

        System.out.println("In message handler");

        if (ev.containsKey("subtype")) {
            System.out.println("ignoring message with subtype: " + ev.get("subtype"));
            return;
        }

        final String text = ev.get("text");
        final String channel = ev.get("channel");
        final String timestamp = ev.get("ts");
        final String userId = ev.get("user");

        final List<Pair<String, String>> translations = fetchChannelLanguages(channel).stream()
                .map(l -> Pair.create(l, translate(l, text)))
                .filter(p -> p._2.isPresent())
                .map(p -> Pair.create(p._1, p._2.get()))
                .collect(Collectors.toList());

        final String altText = text + "\n"
                + translations.stream().map(p -> "_In " + p._1 + ": " + p._2 + "_").collect(Collectors.joining("\n"));

        final boolean updated = updateMessage(userId, channel, timestamp, altText);

        if (!updated) {
            final String userName = fetchUsername(userId).orElse("Somebody");

            translations.stream().map(p -> "_" + userName + " says (" + p._1 + "), \"" + p._2 + "\"_").forEach(
                    x -> postMessage(channel, x));

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

    private void postMessage(final String channel, final String text) {
        final HashMap<String, String> params = new HashMap<>();
        params.put("token", btoken());
        params.put("text", text);
        params.put("channel", channel);

        try {
            final URL u = new URL("https://slack.com/api/chat.postMessage?" + urlEncodeAll(params));
            System.out.println("URL is " + u);
            final HttpURLConnection connection = (HttpURLConnection) u.openConnection();
            connection.setDoInput(true);
            connection.setRequestMethod("POST");
            final int response = connection.getResponseCode();
            if (response < 200 || response >= 300) {
                System.err.println("CRASH: slack returned an error: " + response);
                try (InputStream is = connection.getInputStream()) {
                    pipe(is, System.err);
                }
                return;
            }
        } catch (final Exception x) {
            System.err.println("CRASH: Failure trying to invoke slack API");
            x.printStackTrace(System.err);
            return;
        }

    }

    private <T> boolean some(final Optional<T> maybeValue, final T match) {
        return maybeValue.filter(match::equals).isPresent();
    }

    private Optional<String> translate(final String target, final String text) {

        final HashMap<String, String> params = new HashMap<>();
        params.put("key", gtoken());
        params.put("target", target);
        params.put("q", text);

        try {
            final URL u = new URL("https://www.googleapis.com/language/translate/v2?" + urlEncodeAll(params));
            System.out.println("URL is " + u);
            final HttpURLConnection connection = (HttpURLConnection) u.openConnection();
            connection.setDoInput(true);
            connection.setRequestMethod("GET");
            final int response = connection.getResponseCode();
            try (InputStream is = connection.getInputStream()) {
                if (response < 200 || response >= 300) {
                    System.err.println("ERROR: Google returned an error: " + response);
                    pipe(is, System.err);
                } else {
                    final JsonObject entity = Json.createReader(is).readObject();
                    if (entity.containsKey("data")) {
                        final JsonObject data = entity.getJsonObject("data");
                        if (data.containsKey("translations")) {
                            final JsonArray translations = data.getJsonArray("translations");
                            if (!translations.isEmpty()) {
                                final JsonObject translation = translations.getJsonObject(0);
                                final boolean moveOn;
                                if (translation.containsKey("detectedSourceLanguage")) {
                                    final String sourceLanguage = translation.getString("detectedSourceLanguage");
                                    moveOn = !target.equals(sourceLanguage);
                                } else {
                                    moveOn = true;
                                }
                                if (moveOn) {
                                    if (translation.containsKey("translatedText")) {
                                        final String translatedText = translation.getString("translatedText");
                                        final String correctedText = translatedText.replaceAll("&#39;", "'");
                                        return Optional.of(correctedText);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (final Exception x) {
            System.err.println("ERROR: Failure trying to invoke Google API");
            x.printStackTrace(System.err);
        }

        return Optional.empty();

    }

    private boolean updateMessage(final String userId, final String channel, final String timestamp,
            final String text) {
        final Optional<String> maybeUserToken = utoken(userId);
        if (!maybeUserToken.isPresent())
            return false;
        final HashMap<String, String> params = new HashMap<>();
        params.put("token", maybeUserToken.get());
        params.put("text", text);
        params.put("channel", channel);
        params.put("ts", timestamp);

        try {
            final URL u = new URL("https://slack.com/api/chat.update?" + urlEncodeAll(params));
            System.out.println("URL is " + u);
            final HttpURLConnection connection = (HttpURLConnection) u.openConnection();
            connection.setDoInput(true);
            connection.setRequestMethod("POST");
            final int status = connection.getResponseCode();
            try (InputStream is = connection.getInputStream()) {
                if (status < 200 || status >= 300) {
                    System.err.println("CRASH: slack returned an error: " + status);
                    pipe(is, System.err);
                } else {
                    System.out.println("update response status: " + status);
                    final JsonObject response = Json.createReader(is).readObject();
                    System.out.println(response);
                    return response.getBoolean("ok");
                }
            }
        } catch (final Exception x) {
            System.err.println("CRASH: Failure trying to invoke slack API");
            x.printStackTrace(System.err);
        }
        return false;
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

    private Optional<String> utoken(final String userId) {
        try {
            return Optional.of(DBValueRetriever.fetch(userId, "user:" + userId + ":token", this.users));
        } catch (final Exception x) {
            return Optional.empty();
        }
    }

    private String vtoken() {
        return DBValueRetriever.fetch("global:callbacktoken", this.verificationToken);
    }

    private Collection<String> fetchChannelLanguages(String channel) {

        final String id = "channel:" + channel + ":languages";
        final GetItemRequest getItemRequest = new GetItemRequest()
                .withAttributesToGet(Collections.singletonList("value"))
                .withKey(Collections.singletonMap("id", new AttributeValue(id)))
                .withTableName(TableName);
        final GetItemResult getItemResult = ddb.getItem(getItemRequest);
        Optional<String> maybeValue = Optional.ofNullable(getItemResult.getItem())
                .map(i -> i.get("value"))
                .map(AttributeValue::getS);
        if (!maybeValue.isPresent())
            return Collections.emptyList();

        return Arrays.asList(maybeValue.get().trim().split(" +"));
    }

    public static final class Pair<T1, T2> {
        public static <TT1, TT2> Pair<TT1, TT2> create(final TT1 t1, final TT2 t2) {
            return new Pair<>(t1, t2);
        }

        public final T1 _1;

        public final T2 _2;

        private Pair(final T1 t1, final T2 t2) {
            this._1 = t1;
            this._2 = t2;
        }

    }

}
