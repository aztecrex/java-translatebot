package com.banjocreek.translate;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.BatchWriteItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutRequest;
import com.amazonaws.services.dynamodbv2.model.WriteRequest;

public class OauthHandler {

    public final Map<String, Object> handle(final Map<String, String> in) {

        final Map<String, String> authData = generateOutput(in);
        final String authBody = urlEncode(authData);

        try {
            final URL u = new URL("https://slack.com/api/oauth.access?" + authBody);
            System.out.println(u);
            final HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setDoInput(true);
            conn.setRequestMethod("POST");
            try (InputStream is = conn.getInputStream();) {
                final JsonReader reader = Json.createReader(is);
                final JsonObject slackAuth = reader.readObject();
                final Map<String, Object> result = plainifyJsonObject(slackAuth);
                storeResult(result);
                return Collections.singletonMap("ok", true);
            } finally {
                conn.disconnect();
            }
        } catch (final Exception x) {
            System.err.println("Uh Oh");
            x.printStackTrace(System.err);
            return Collections.singletonMap("error", x.getMessage());
        }

    }

    private static final AmazonDynamoDBClient ddb = new AmazonDynamoDBClient();
    private static final String TableName = "TranslateSlack";

    private void storeResult(Map<String, Object> result) {
        final String userId = (String) result.get("user_id");
        final String userToken = (String) result.get("access_token");
        @SuppressWarnings("unchecked")
        final Map<String, Object> bot = (Map<String, Object>) result.get("bot");
        final String botToken = (String) bot.get("bot_access_token");

        ArrayList<PutRequest> putRequests = new ArrayList<>();
        final HashMap<String, AttributeValue> userItem = new HashMap<>();
        userItem.put("id", new AttributeValue("user:" + userId + ":token"));
        userItem.put("value", new AttributeValue(userToken));
        putRequests.add(new PutRequest().withItem(userItem));

        final HashMap<String, AttributeValue> botItem = new HashMap<>();
        botItem.put("id", new AttributeValue("global:bottoken"));
        botItem.put("value", new AttributeValue(botToken));
        putRequests.add(new PutRequest().withItem(botItem));

        final List<WriteRequest> writeRequests = putRequests.stream()
                .map(WriteRequest::new)
                .collect(Collectors.toList());
        HashMap<String, List<WriteRequest>> requestItems = new HashMap<>();
        requestItems.put(TableName, writeRequests);
        final BatchWriteItemRequest batchWriteItemRequest = new BatchWriteItemRequest().withRequestItems(requestItems);

        ddb.batchWriteItem(batchWriteItemRequest);
    }

    private final String urlEncode(final Map<String, String> params) {
        return params.entrySet().stream().map(this::param).collect(Collectors.joining("&"));
    }

    private Map<String, String> generateOutput(final Map<String, String> in) {

        final HashMap<String, String> rval = new HashMap<>();
        rval.put("code", in.get("code"));
        rval.put("client_id", "76454819904.83569102723");
        rval.put("client_secret", "8217a395d876bb8a367c9379cac2a7e4");
        return Collections.unmodifiableMap(rval);
    }

    private final String param(final Entry<String, String> entry) {
        return param(entry.getKey(), entry.getValue());
    }

    private final String param(final String name, final String value) {
        try {
            return new StringBuffer().append(name).append("=").append(URLEncoder.encode(value, "UTF-8")).toString();
        } catch (final UnsupportedEncodingException e) {
            throw new RuntimeException("cannot encode value", e);
        }
    }

    private List<Object> plainifyJsonArray(final JsonArray jary) {
        return jary.stream().map(this::plainifyJsonValue).collect(Collectors.toList());
    }

    private Map<String, Object> plainifyJsonObject(final JsonObject jobj) {
        final HashMap<String, Object> rval = new HashMap<>();
        jobj.entrySet().forEach(e -> rval.put(e.getKey(), plainifyJsonValue(e.getValue())));
        return Collections.unmodifiableMap(rval);
    }

    private Object plainifyJsonValue(final JsonValue jval) {
        switch (jval.getValueType()) {
        case ARRAY:
            return plainifyJsonArray((JsonArray) jval);
        case FALSE:
            return Boolean.FALSE;
        case TRUE:
            return Boolean.TRUE;
        case NULL:
            return null;
        case NUMBER:
            return ((JsonNumber) jval).bigDecimalValue();
        case OBJECT:
            return plainifyJsonObject((JsonObject) jval);
        case STRING:
            return ((JsonString) jval).getString();
        default:
            throw new RuntimeException("unexpected json type");
        }
    }

}
