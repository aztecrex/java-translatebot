package com.banjocreek.translatebot;

import java.util.Collections;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;

public class DBValueRetriever {

    private static final AmazonDynamoDBClient ddb = new AmazonDynamoDBClient();

    private static final String TableName = "TranslateSlack";

    private boolean fired = false;

    private final String id;

    private final Object monitor = new Object();
    private String v = null;
    private RuntimeException x = null;

    public DBValueRetriever(final String id) {
        this.id = id;
    }

    public String get() {

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

        final GetItemRequest req = new GetItemRequest().withAttributesToGet("value")
                .withTableName(TableName)
                .withKey(Collections.singletonMap("id", new AttributeValue(this.id)));
        try {
            final GetItemResult result = ddb.getItem(req);
            synchronized (this.monitor) {
                if (result.getItem() == null) {
                    this.x = new RuntimeException("not found: id=" + this.id);
                } else {
                    this.v = result.getItem().get("value").getS();
                    if (this.v == null) {
                        this.x = new RuntimeException("found but no value for: id=" + this.id);
                    }
                }
            }
        } catch (final RuntimeException x) {
            synchronized (this.monitor) {
                this.x = x;
            }
        }

    }
}
