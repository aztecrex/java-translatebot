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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;

public class CommandHandler {

    private static final AmazonDynamoDBClient ddb = new AmazonDynamoDBClient();

    private final AtomicReference<DBValueRetriever> verificationToken = new AtomicReference<DBValueRetriever>();

    private final AtomicReference<DBValueRetriever> googleToken = new AtomicReference<DBValueRetriever>();

    private final AtomicReference<LanguageRetriever> supportedLanguages = new AtomicReference<>();

    private static final String TableName = "TranslateSlack";

    public Map<String, Object> handle(Map<String, String> in) {

        if (!in.get("token").equals(vtoken())) {
            return Collections.emptyMap();
        }

        String rawCommand = in.get("text").trim();
        String[] command = rawCommand.split(" +");
        if (command.length == 0 || command[0].equals("help")) {
            return Collections.singletonMap("text",
                    "_add languages to channel:_ add <lang> ...\n"
                            + "_remove languages from channel:_ remove <lang> ...\n" + "_list languages:_ languages");
        }

        String channel = in.get("channel_id");
        // String team = in.get("team_id");
        final Set<String> languages = languages();
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
        } else {
            return Collections.singletonMap("text", "unrecognized subcommand '" + command[0] + "'");
        }

    }

    private void setChannelLanguages(String channel, HashSet<String> curlangs) {

        final String id = "channel:" + channel + ":languages";

        final HashMap<String, AttributeValue> item = new HashMap<>();
        final String value = curlangs.stream().collect(Collectors.joining(" "));
        item.put("id", new AttributeValue(id));
        item.put("value", new AttributeValue(value));
        PutItemRequest putItemRequest = new PutItemRequest().withItem(item)
                .withTableName(TableName);
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

    private Set<String> languages() {
        return LanguageRetriever.fetch(gtoken(), supportedLanguages);
    }

    /*
     * 
     * token=gIkuvaNzQIHg97ATvDxqgjtO
     * 
     * team_id=T0001
     * 
     * team_domain=example
     * 
     * channel_id=C2147483705
     * 
     * channel_name=test
     * 
     * user_id=U2147483697
     * 
     * user_name=Steve
     * 
     * command=/weather
     * 
     * text=94070
     * 
     * response_url=https://hooks.slack.com/commands/1234/5678
     * 
     */

    private String gtoken() {
        return DBValueRetriever.fetch("global:googletoken", this.googleToken);
    }

    private String vtoken() {
        return DBValueRetriever.fetch("global:callbacktoken", this.verificationToken);
    }
}
