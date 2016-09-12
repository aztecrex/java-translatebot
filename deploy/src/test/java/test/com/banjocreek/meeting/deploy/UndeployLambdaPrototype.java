package test.com.banjocreek.meeting.deploy;

import com.amazonaws.services.lambda.AWSLambdaAsyncClient;
import com.amazonaws.services.lambda.model.DeleteFunctionRequest;

public class UndeployLambdaPrototype {

    public static void main(final String[] args) {

        final AWSLambdaAsyncClient client = new AWSLambdaAsyncClient();

        final DeleteFunctionRequest dlfrq = new DeleteFunctionRequest().withFunctionName("Info2");

        client.deleteFunction(dlfrq);

    }

}
