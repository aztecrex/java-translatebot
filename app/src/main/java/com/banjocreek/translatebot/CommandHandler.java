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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonString;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;

public class CommandHandler {

    private static final AmazonDynamoDBClient ddb = new AmazonDynamoDBClient();

    private static final String TableName = "TranslateSlack";

    public Map<String, Object> handle(final Map<String, String> in) {

        if (!in.get("token").equals(vtoken()))
            return Collections.emptyMap();

        final String channel = in.get("channel_id");
        final String team = in.get("team_id");
        if (!botInChannel(team, channel))
            return Collections.singletonMap("text",
                    "Not translating in this channel, */invite @borges* to enable translation");

        final String rawCommand = in.get("text").trim();
        final String[] command = rawCommand.split(" +");

        if (command.length == 0 || command[0].equals("help"))
            return Collections.singletonMap("text",
                    "add languages to channel: */borges add <lang> ...*\n"
                            + "remove languages from channel: */borges remove <lang> ...*\n"
                            + "list supported languages: */borges languages*\n"
                            + "show channel configuration: */borges show*\n"
                            + "configure Google API token: */borges configure <google-auth-token>*");

        if (command[0].equals("configure")) {
            if (command.length < 2)
                return Collections.singletonMap("text", "_usage_: /borges configure <google-auth-token>");
            final Optional<String> maybePromoToken = promo(command[1]);
            final String token = maybePromoToken.orElse(command[1]);
            if (!isValidGoogleToken(token))
                return Collections.singletonMap("text", "A valid google token or Borges promo code is required");
            setTeamConfiguration(team, token);
            if (maybePromoToken.isPresent()) {
                unpromo(command[1]);
            }
            return Collections.singletonMap("text", "configured");
        }

        final Optional<String> maybeGoogleToken = googleToken(team);
        if (!maybeGoogleToken.isPresent())
            return Collections.singletonMap("text",
                    "You need to configure your Google Translate API Credentials, try /borges help");

        final String googleToken = maybeGoogleToken.get();

        final Set<String> languages = languages(googleToken);
        if (command[0].equals("add") || command[0].equals("remove")) {
            final ArrayList<String> inlangs = new ArrayList<>();
            for (int i = 1; i < command.length; ++i) {
                if (!languages.contains(command[i]))
                    return Collections.singletonMap("text", "unsupported language '" + command[i] + "'");
                inlangs.add(command[i]);
            }
            final HashSet<String> curlangs = new HashSet<>(fetchChannelLanguages(channel));
            if (command[0].equalsIgnoreCase("add")) {
                curlangs.addAll(inlangs);
            } else {
                curlangs.removeAll(inlangs);
            }
            setChannelLanguages(channel, curlangs);
            final String resp = "now translating: " + curlangs.stream().collect(Collectors.joining(" "));
            return Collections.singletonMap("text", resp);
        } else if (command[0].equals("languages")) {
            final String resp = "Supported languages: "
                    + new TreeSet<>(languages).stream().collect(Collectors.joining(" "));
            return Collections.singletonMap("text", resp);
        } else if (command[0].equals("show")) {
            final Collection<String> current = fetchChannelLanguages(channel);
            final String resp;
            if (current.isEmpty()) {
                resp = "not translating in this channel, use _/borges add <lang> ..._ to add languages";
            } else {
                resp = "currently translating: "
                        + fetchChannelLanguages(channel).stream().collect(Collectors.joining(" "));
            }
            return Collections.singletonMap("text", resp);
        } else
            return Collections.singletonMap("text", "unrecognized subcommand '" + command[0] + "'");

    }

    private boolean botInChannel(final String team, final String channel) {
        final String bot = botUser(team);
        final String botToken = utoken(bot);
        try {
            return channelInfo(botToken, channel).get()
                    .getJsonObject("channel")
                    .getJsonArray("members")
                    .stream()
                    .map(jv -> ((JsonString) jv).getString())
                    .filter(bot::equals)
                    .findAny()
                    .isPresent();
        } catch (final Exception x) {
            return false;
        }
    }

    private String botUser(final String teamId) {
        final String id = "team:" + teamId + ":botuser";
        return new DBValueRetriever(id).get();
    }

    private Optional<JsonObject> channelInfo(final String authToken, final String channel) {

        final HashMap<String, String> params = new HashMap<>();
        params.put("token", authToken);
        params.put("channel", channel);

        try {
            final URL u = new URL("https://slack.com/api/channels.info?" + urlEncodeAll(params));
            System.out.println("URL is " + u);
            final HttpURLConnection connection = (HttpURLConnection) u.openConnection();
            connection.setDoInput(true);
            connection.setRequestMethod("POST");
            final int response = connection.getResponseCode();
            try (InputStream is = connection.getInputStream()) {
                if (response < 200 || response >= 300) {
                    System.err.println("CRASH: slack returned an error: " + response);
                    pipe(is, System.err);
                } else {
                    final JsonObject rval = Json.createReader(is).readObject();
                    if (rval.getBoolean("ok"))
                        return Optional.of(rval);
                }
            }
        } catch (final Exception x) {
            System.err.println("CRASH: Failure trying to invoke slack API");
            x.printStackTrace(System.err);
        }
        return Optional.empty();
    }

    private Collection<String> fetchChannelLanguages(final String channel) {

        final String id = "channel:" + channel + ":languages";
        final GetItemRequest getItemRequest = new GetItemRequest()
                .withAttributesToGet(Collections.singletonList("value"))
                .withKey(Collections.singletonMap("id", new AttributeValue(id)))
                .withTableName(TableName);
        final GetItemResult getItemResult = ddb.getItem(getItemRequest);
        final Optional<String> maybeValue = Optional.ofNullable(getItemResult.getItem())
                .map(i -> i.get("value"))
                .map(AttributeValue::getS);
        if (!maybeValue.isPresent())
            return Collections.emptyList();

        return Arrays.asList(maybeValue.get().trim().split(" +"));
    }

    private Optional<String> googleToken(final String team) {
        final String id = "team:" + team + ":googletoken";
        try {
            return Optional.of(new DBValueRetriever(id).get());
        } catch (final Exception x) {
            return Optional.empty();
        }
    }

    private boolean isValidGoogleToken(final String token) {
        try {
            new LanguageRetriever(token).get();
            return true;
        } catch (final Exception x) {
            return false;
        }
    }

    private Set<String> languages(final String authToken) {
        return new LanguageRetriever(authToken).get();
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

    private Optional<String> promo(final String code) {
        final String id = "promo:" + code;
        try {
            return Optional.of(new DBValueRetriever(id).get());
        } catch (final Exception x) {
            return Optional.empty();
        }
    }

    private void setChannelLanguages(final String channel, final HashSet<String> curlangs) {

        final String id = "channel:" + channel + ":languages";

        if (curlangs.isEmpty()) {
            ddb.deleteItem(TableName, Collections.singletonMap("id", new AttributeValue(id)));
        } else {
            final HashMap<String, AttributeValue> item = new HashMap<>();
            final String value = curlangs.stream().collect(Collectors.joining(" "));
            item.put("id", new AttributeValue(id));
            item.put("value", new AttributeValue(value));
            final PutItemRequest putItemRequest = new PutItemRequest().withItem(item).withTableName(TableName);
            ddb.putItem(putItemRequest);
        }
    }

    private void setTeamConfiguration(final String team, final String authToken) {

        final String id = "team:" + team + ":googletoken";
        final HashMap<String, AttributeValue> item = new HashMap<>();
        final String value = authToken;
        item.put("id", new AttributeValue(id));
        item.put("value", new AttributeValue(value));
        final PutItemRequest putItemRequest = new PutItemRequest().withItem(item).withTableName(TableName);
        ddb.putItem(putItemRequest);
    }

    private void unpromo(final String code) {
        final String id = "promo:" + code;
        try {
            ddb.deleteItem(TableName, Collections.singletonMap("id", new AttributeValue(id)));
        } catch (final Exception x) {
            // OK, just no item with that name
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

    private String utoken(final String userId) {
        final String id = "user:" + userId + ":token";
        return new DBValueRetriever(id).get();
    }

    private String vtoken() {
        return new DBValueRetriever("global:callbacktoken").get();
    }

}
