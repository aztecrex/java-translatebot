package com.banjocreek.translate;

import java.util.Collections;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;

public class DBValueRetriever {

    private static final String TableName = "TranslateSlack";

    private static final AmazonDynamoDBClient ddb = new AmazonDynamoDBClient();

    private final String id;

    private final Object monitor = new Object();

    private boolean fired = false;
    private RuntimeException x = null;
    private String v = null;

    public DBValueRetriever(String id) {
        this.id = id;
    }

    private void fetchTheValue() {

        final GetItemRequest req = new GetItemRequest().withAttributesToGet("value")
                .withTableName(TableName)
                .withKey(Collections.singletonMap("id", new AttributeValue(id)));
        try {
            GetItemResult result = ddb.getItem(req);
            synchronized (monitor) {
                if (result.getItem() == null)
                    this.x = new RuntimeException("not found: id=" + id);
                else {
                    this.v = result.getItem().get("value").getS();
                    if (this.v == null)
                        this.x = new RuntimeException("found but no value for: id=" + id);
                }
            }
        } catch (RuntimeException x) {
            synchronized (monitor) {
                this.x = x;
            }
        }

    }

    private boolean elect() {
        synchronized (monitor) {
            if (fired)
                return false;
            else {
                fired = true;
                return true;
            }
        }
    }

    public String get() {

        if (elect())
            fetchTheValue();

        synchronized (monitor) {
            try {
                while (v == null && x == null)
                    monitor.wait();
            } catch (InterruptedException ix) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("interrupted");
            }
            if (x != null)
                throw x;
            else if (v != null)
                return v;
            else
                throw new AssertionError("should never get here");
        }
    }
}
