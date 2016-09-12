package test.com.banjocreek.meeting.deploy;

import java.util.Collections;

import com.amazonaws.services.apigateway.AmazonApiGatewayAsyncClient;
import com.amazonaws.services.apigateway.model.CreateResourceRequest;
import com.amazonaws.services.apigateway.model.CreateResourceResult;
import com.amazonaws.services.apigateway.model.CreateRestApiRequest;
import com.amazonaws.services.apigateway.model.CreateRestApiResult;
import com.amazonaws.services.apigateway.model.GetResourcesRequest;
import com.amazonaws.services.apigateway.model.IntegrationType;
import com.amazonaws.services.apigateway.model.PutIntegrationRequest;
import com.amazonaws.services.apigateway.model.PutMethodRequest;
import com.amazonaws.services.apigateway.model.PutMethodResult;
import com.amazonaws.services.lambda.AWSLambdaAsyncClient;

public class CreateApiPrototype {
    public static void main(final String[] args) {

        final AWSLambdaAsyncClient lambda = new AWSLambdaAsyncClient();
        final AmazonApiGatewayAsyncClient api = new AmazonApiGatewayAsyncClient();

        final CreateRestApiRequest caprq = new CreateRestApiRequest().withName("ChatMeeting")
                .withDescription("Chat integration for meetings");
        final CreateRestApiResult caprs = api.createRestApi(caprq);
        System.out.println("rest api id: " + caprs.getId());

        final String rootId = api.getResources(new GetResourcesRequest().withRestApiId(caprs.getId()))
                .getItems()
                .stream()
                .filter(r -> r.getPath().equals("/"))
                .findAny()
                .map(r -> r.getId())
                .get(); // root better exist

        final CreateResourceRequest crrq = new CreateResourceRequest().withParentId("")
                .withParentId(rootId)
                .withPathPart("meet")
                .withRestApiId(caprs.getId());
        final CreateResourceResult crrs = api.createResource(crrq);
        System.out.println("resource id: " + crrs.getId());

        final PutMethodRequest pmrq = new PutMethodRequest().withHttpMethod("POST")
                .withRestApiId(caprs.getId())
                .withResourceId(crrs.getId())
                .withAuthorizationType("NONE");
        final PutMethodResult pmrs = api.putMethod(pmrq);

        final String iarn = lambda.listFunctions()
                .getFunctions()
                .stream()
                .filter(f -> f.getFunctionName().equals("Info2"))
                .findAny()
                .map(f -> f.getFunctionArn())
                .map(farn -> "arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/" + farn + "/invocations")
                .get();

        final String template = "";

        final PutIntegrationRequest pirq = new PutIntegrationRequest().withType(IntegrationType.AWS)
                .withRestApiId(caprs.getId())
                .withResourceId(crrs.getId())
                .withHttpMethod("POST")
                .withIntegrationHttpMethod("POST")
                .withUri(iarn)
                .withRequestTemplates(Collections.singletonMap("application/x-www-form-urlencoded", template));
        System.out.println("lambda uri: " + pirq.getUri());
        api.putIntegration(pirq);

    }
}
