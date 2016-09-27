package com.banjocreek.translatebot.deploy;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;

public class DatabaseDeployer {

    private static AmazonDynamoDBClient ddb = new AmazonDynamoDBClient();

    public void deploy() {

        final AttributeDefinition idAttr = new AttributeDefinition().withAttributeName("id")
                .withAttributeType(ScalarAttributeType.S);
        final ProvisionedThroughput throughput = new ProvisionedThroughput().withReadCapacityUnits(5L)
                .withWriteCapacityUnits(5L);

        final KeySchemaElement idKey = new KeySchemaElement().withAttributeName("id").withKeyType(KeyType.HASH);

        final CreateTableRequest createTableRequest = new CreateTableRequest().withTableName("TranslateSlack")
                .withAttributeDefinitions(idAttr)
                .withKeySchema(idKey)
                .withProvisionedThroughput(throughput);
        ;
        ;

        ddb.createTable(createTableRequest);

    }

}
