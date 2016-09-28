package com.banjocreek.translatebot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;

public class CommandHandler {

    private static final AmazonDynamoDBClient ddb = new AmazonDynamoDBClient();

    private static final String TableName = "TranslateSlack";

    public Map<String, Object> handle(Map<String, String> in) {

        if (!in.get("token").equals(vtoken())) {
            return Collections.emptyMap();
        }

        String rawCommand = in.get("text").trim();
        String[] command = rawCommand.split(" +");

        final String team = in.get("team_id");

        if (command.length == 0 || command[0].equals("help")) {
            return Collections.singletonMap("text",
                    "_add languages to channel:_ /borges add <lang> ...\n"
                            + "_remove languages from channel:_ /borges remove <lang> ...\n"
                            + "_list supported languages:_ /borges languages\n"
                            + "_show channel configuration:_ /borges show\n"
                            + "_configure google translate api token:_ /borges configure <google-auth-token>");
        }

        if (command[0].equals("configure")) {
            if (command.length < 2) {
                return Collections.singletonMap("text", "_usage_: /borges configure <google-auth-token>");
            }
            setTeamConfiguration(team, command[1]);
            return Collections.singletonMap("text", "configured");
        }

        Optional<String> maybeGoogleToken = googleToken(team);
        if (!maybeGoogleToken.isPresent()) {
            return Collections.singletonMap("text",
                    "You need to configure your Google Translate API Credentials, try /borges help");
        }

        final String googleToken = maybeGoogleToken.get();

        String channel = in.get("channel_id");
        final Set<String> languages = languages(googleToken);
        if (command[0].equals("add") || command[0].equals("remove")) {
            final ArrayList<String> inlangs = new ArrayList<>();
            for (int i = 1; i < command.length; ++i) {
                if (!languages.contains(command[i]))
                    return Collections.singletonMap("text", "unsupported language '" + command[i] + "'");
                inlangs.add(command[i]);
            }
            final HashSet<String> curlangs = new HashSet<>(fetchChannelLanguages(channel));
            if (command[0].equalsIgnoreCase("add"))
                curlangs.addAll(inlangs);
            else
                curlangs.removeAll(inlangs);
            setChannelLanguages(channel, curlangs);
            final String resp = "now translating: " + curlangs.stream().collect(Collectors.joining(" "));
            return Collections.singletonMap("text", resp);
        } else if (command[0].equals("languages")) {
            final String resp = new TreeSet<>(languages).stream().collect(Collectors.joining("\n"));
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
        } else {
            return Collections.singletonMap("text", "unrecognized subcommand '" + command[0] + "'");
        }

    }

    private void setTeamConfiguration(String team, String authToken) {

        final String id = "team:" + team + ":googletoken";
        final HashMap<String, AttributeValue> item = new HashMap<>();
        final String value = authToken;
        item.put("id", new AttributeValue(id));
        item.put("value", new AttributeValue(value));
        PutItemRequest putItemRequest = new PutItemRequest().withItem(item).withTableName(TableName);
        ddb.putItem(putItemRequest);
    }

    private void setChannelLanguages(String channel, HashSet<String> curlangs) {

        final String id = "channel:" + channel + ":languages";

        final HashMap<String, AttributeValue> item = new HashMap<>();
        final String value = curlangs.stream().collect(Collectors.joining(" "));
        item.put("id", new AttributeValue(id));
        item.put("value", new AttributeValue(value));
        PutItemRequest putItemRequest = new PutItemRequest().withItem(item).withTableName(TableName);
        ddb.putItem(putItemRequest);
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

    private Set<String> languages(String authToken) {
        return new LanguageRetriever(authToken).get();
    }

    private Optional<String> googleToken(String team) {
        final String id = "team:" + team + ":googletoken";
        final GetItemRequest getItemRequest = new GetItemRequest()
                .withAttributesToGet(Collections.singletonList("value"))
                .withKey(Collections.singletonMap("id", new AttributeValue(id)))
                .withTableName(TableName);
        final GetItemResult getItemResult = ddb.getItem(getItemRequest);
        return Optional.ofNullable(getItemResult.getItem()).map(i -> i.get("value")).map(AttributeValue::getS);
    }

    private String vtoken() {
        return new DBValueRetriever("global:callbacktoken").get();
    }
}
