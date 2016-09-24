package test.com.banjocreek.meeting.deploy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Random;
import java.util.stream.Collectors;

import com.amazonaws.services.apigateway.AmazonApiGatewayAsyncClient;
import com.amazonaws.services.apigateway.model.CreateDeploymentRequest;
import com.amazonaws.services.apigateway.model.CreateDeploymentResult;
import com.amazonaws.services.apigateway.model.CreateResourceRequest;
import com.amazonaws.services.apigateway.model.CreateResourceResult;
import com.amazonaws.services.apigateway.model.CreateRestApiRequest;
import com.amazonaws.services.apigateway.model.CreateRestApiResult;
import com.amazonaws.services.apigateway.model.GetResourcesRequest;
import com.amazonaws.services.apigateway.model.IntegrationType;
import com.amazonaws.services.apigateway.model.PutIntegrationRequest;
import com.amazonaws.services.apigateway.model.PutIntegrationResponseRequest;
import com.amazonaws.services.apigateway.model.PutMethodRequest;
import com.amazonaws.services.apigateway.model.PutMethodResponseRequest;
import com.amazonaws.services.lambda.AWSLambdaAsyncClient;
import com.amazonaws.services.lambda.model.AddPermissionRequest;

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
        api.putMethod(pmrq);

        
        
        final String iarn = lambda.listFunctions()
                .getFunctions()
                .stream()
                .filter(f -> f.getFunctionName().equals("Info2"))
                .findAny()
                .map(f -> f.getFunctionArn())
                .map(farn -> "arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/" + farn + "/invocations")
                .get();

        final String template = loadIntegrationTemplate();
        final PutIntegrationRequest pirq = new PutIntegrationRequest().withType(IntegrationType.AWS)
                .withRestApiId(caprs.getId())
                .withResourceId(crrs.getId())
                .withHttpMethod("POST")
                .withIntegrationHttpMethod("POST")
                .withUri(iarn)
                .withPassthroughBehavior("NEVER")
                .withRequestTemplates(Collections.singletonMap("application/x-www-form-urlencoded", template));
        api.putIntegration(pirq);

        final PutIntegrationResponseRequest pirsrq = new PutIntegrationResponseRequest()
                .withRestApiId(caprs.getId())
                .withResourceId(crrs.getId())
                .withHttpMethod("POST")
                .withStatusCode("200")
                .withResponseTemplates(Collections.singletonMap("application/json", ""))
                ;
        api.putIntegrationResponse(pirsrq);

        final PutMethodResponseRequest pmrsrq = new PutMethodResponseRequest()
                .withRestApiId(caprs.getId())
                .withResourceId(crrs.getId())
                .withHttpMethod("POST")
                .withStatusCode("200")
                .withResponseModels(Collections.singletonMap("application/json", "Empty"))
                ;
        api.putMethodResponse(pmrsrq);
        
        final CreateDeploymentRequest cdrq = new CreateDeploymentRequest().withRestApiId(caprs.getId())
                .withStageName("advance");
        CreateDeploymentResult cdrs = api.createDeployment(cdrq);
        System.out.println("deployment id: " + cdrs.getId());



        // such a hack
        String accountId = iarn.split(":")[9];
        String sourceArn = "arn:aws:execute-api:us-east-1:"
                + accountId + ":" + caprs.getId()  + "/*/POST/meet";
        System.out.println(sourceArn);
        Random rng = new SecureRandom();
        // allow api to invoke lambda function
        AddPermissionRequest aprq  = new AddPermissionRequest()
                .withFunctionName("Info2")
                .withAction("lambda:InvokeFunction")
                .withPrincipal("apigateway.amazonaws.com")
                .withStatementId("sid" + Math.abs(rng.nextLong()))
                .withSourceArn(sourceArn)
                ;
        lambda.addPermission(aprq);
        
        
        
    }

    private static String loadIntegrationTemplate() {

        try (final InputStream ts = CreateApiPrototype.class.getResourceAsStream("integration-request.vel");
                final Reader tr = new InputStreamReader(ts, "UTF-8");
                final BufferedReader br = new BufferedReader(tr)) {
            return br.lines().collect(Collectors.joining("\n"));
        } catch (IOException iox) {
            throw new RuntimeException("io error loading integration template", iox);
        }

    }

}
