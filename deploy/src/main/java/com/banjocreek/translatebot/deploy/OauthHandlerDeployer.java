package com.banjocreek.translatebot.deploy;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;

import com.amazonaws.services.apigateway.AmazonApiGatewayClient;
import com.amazonaws.services.apigateway.model.CreateDeploymentRequest;
import com.amazonaws.services.apigateway.model.CreateDeploymentResult;
import com.amazonaws.services.apigateway.model.CreateResourceRequest;
import com.amazonaws.services.apigateway.model.CreateResourceResult;
import com.amazonaws.services.apigateway.model.CreateRestApiRequest;
import com.amazonaws.services.apigateway.model.CreateRestApiResult;
import com.amazonaws.services.apigateway.model.DeleteRestApiRequest;
import com.amazonaws.services.apigateway.model.GetResourcesRequest;
import com.amazonaws.services.apigateway.model.GetRestApisRequest;
import com.amazonaws.services.apigateway.model.GetRestApisResult;
import com.amazonaws.services.apigateway.model.IntegrationType;
import com.amazonaws.services.apigateway.model.PutIntegrationRequest;
import com.amazonaws.services.apigateway.model.PutIntegrationResponseRequest;
import com.amazonaws.services.apigateway.model.PutMethodRequest;
import com.amazonaws.services.apigateway.model.PutMethodResponseRequest;
import com.amazonaws.services.apigateway.model.RestApi;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.lambda.model.AddPermissionRequest;
import com.amazonaws.services.lambda.model.CreateFunctionRequest;
import com.amazonaws.services.lambda.model.CreateFunctionResult;
import com.amazonaws.services.lambda.model.DeleteFunctionRequest;
import com.amazonaws.services.lambda.model.FunctionCode;
import com.amazonaws.services.lambda.model.ResourceNotFoundException;
import com.amazonaws.services.lambda.model.Runtime;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeRequest;

public class OauthHandlerDeployer {
    private static final String ApiName = "TranslatorSlackOauth";

    private static final String FunctionName = "TranslatorSlackOauth";

    private static final String HandlerSpec = "com.banjocreek.translatebot.OauthHandler::handle";

    private static final String HttpMethod = "GET";

    private static final String Path = "auth";

    private static final Random rng = new SecureRandom();

    private final AmazonApiGatewayClient awsApiClient = new AmazonApiGatewayClient();

    private final AWSLambdaClient awsLambdaClient = new AWSLambdaClient();

    public void deploy() {

        final CreateFunctionResult function = createFunction();

        createApi(function);

    }

    public void undeploy() {

        deleteApi();
        deleteFunction();

    }

    public void updateFunction() {

        final UpdateFunctionCodeRequest ufrq = new UpdateFunctionCodeRequest().withFunctionName(FunctionName)
                .withZipFile(loadJar());

        this.awsLambdaClient.updateFunctionCode(ufrq);

    }

    private String accountId(final String iarn) {
        return iarn.split(":")[9];
    }

    private CreateRestApiResult createApi() {
        final CreateRestApiRequest caprq = new CreateRestApiRequest().withName(ApiName)
                .withDescription("Slack event handler for translator");
        return this.awsApiClient.createRestApi(caprq);

    }

    private void createApi(final CreateFunctionResult function) {

        final CreateRestApiResult createApiResult = createApi();
        final CreateResourceResult crrs = createResource(createApiResult);

        final String iarn = iarn(function);

        createMethod(createApiResult, crrs, iarn);

        stageApi(createApiResult);

        final String accountId = accountId(iarn);

        permitInvokeLambda(accountId, createApiResult, function);

    }

    private CreateFunctionResult createFunction() {

        final String executionRole = initExecutionRole();
        final FunctionCode fcod = loadCode();

        final CreateFunctionRequest cfrq = new CreateFunctionRequest().withCode(fcod)
                .withDescription("Translator Slack event handler")
                .withFunctionName(FunctionName)
                .withHandler(HandlerSpec)
                .withMemorySize(256)
                .withPublish(true)
                .withRole(executionRole)
                .withRuntime(Runtime.Java8);

        return this.awsLambdaClient.createFunction(cfrq);

    }

    private void createMethod(final CreateRestApiResult createApiResult, final CreateResourceResult crrs,
            final String iarn) {
        final HashMap<String, Boolean> params = new HashMap<>();
        params.put("method.request.querystring.code", true);
        params.put("method.request.querystring.state", false);
        final PutMethodRequest pmrq = new PutMethodRequest().withRestApiId(createApiResult.getId())
                .withResourceId(crrs.getId())
                .withHttpMethod(HttpMethod)
                .withRequestParameters(params)
                .withAuthorizationType("NONE");
        this.awsApiClient.putMethod(pmrq);
        final String tplt = "{\"code\":\"$input.params('code')\",\"state\":\"$input.params('state')\"}";
        final PutIntegrationRequest pirq = new PutIntegrationRequest().withType(IntegrationType.AWS)
                .withRestApiId(createApiResult.getId())
                .withResourceId(crrs.getId())
                .withHttpMethod(HttpMethod)
                .withIntegrationHttpMethod("POST")
                .withPassthroughBehavior("NEVER")
                .withRequestTemplates(Collections.singletonMap("application/json", tplt))
                .withUri(iarn);

        this.awsApiClient.putIntegration(pirq);

        final PutIntegrationResponseRequest pirsrq = new PutIntegrationResponseRequest()
                .withRestApiId(createApiResult.getId())
                .withResourceId(crrs.getId())
                .withHttpMethod(HttpMethod)
                .withStatusCode("200")
                .withResponseTemplates(Collections.singletonMap("application/json", ""));
        this.awsApiClient.putIntegrationResponse(pirsrq);

        final PutMethodResponseRequest pmrsrq = new PutMethodResponseRequest().withRestApiId(createApiResult.getId())
                .withResourceId(crrs.getId())
                .withHttpMethod(HttpMethod)
                .withStatusCode("200")
                .withResponseModels(Collections.singletonMap("application/json", "Empty"));
        this.awsApiClient.putMethodResponse(pmrsrq);
    }

    private CreateResourceResult createResource(final CreateRestApiResult createApiResult) {

        final String rootId = this.awsApiClient
                .getResources(new GetResourcesRequest().withRestApiId(createApiResult.getId()))
                .getItems()
                .stream()
                .filter(r -> r.getPath().equals("/"))
                .findAny()
                .map(r -> r.getId())
                .get(); // root better exist

        final CreateResourceRequest crrq = new CreateResourceRequest().withParentId("")
                .withParentId(rootId)
                .withPathPart(Path)
                .withRestApiId(createApiResult.getId());
        return this.awsApiClient.createResource(crrq);
    }

    private void deleteApi() {
        final GetRestApisRequest graprq = new GetRestApisRequest();

        final GetRestApisResult graprs = this.awsApiClient.getRestApis(graprq);

        graprs.getItems()
                .stream()
                .filter(ra -> ra.getName().equals(ApiName))
                .map(RestApi::getId)
                .map(id -> new DeleteRestApiRequest().withRestApiId(id))
                .forEach(this.awsApiClient::deleteRestApi);

    }

    private void deleteFunction() {

        final DeleteFunctionRequest dlfrq = new DeleteFunctionRequest().withFunctionName(FunctionName);
        try {
            this.awsLambdaClient.deleteFunction(dlfrq);
        } catch (final ResourceNotFoundException rnfx) {
            // it is ok if the resource is not there, we simply succeed
        }

    }

    private String iarn(final CreateFunctionResult function) {
        final String rval = this.awsLambdaClient.listFunctions()
                .getFunctions()
                .stream()
                .filter(f -> f.getFunctionName().equals(function.getFunctionName()))
                .findAny()
                .map(f -> f.getFunctionArn())
                .map(farn -> "arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/" + farn + "/invocations")
                .get();
        System.out.println("farn: " + rval);
        return rval;
    }

    private String initExecutionRole() {
        // for distribution, need to create role or consult config
        return "arn:aws:iam::299766559344:role/service-role/LambdaExplorerRole";
    }

    private FunctionCode loadCode() {

        return new FunctionCode().withZipFile(loadJar());
    }

    private ByteBuffer loadJar() {
        final File jarfile = new File(
                "/Users/aztecrex/Code/java-translatebot/app/target/translatebot-app-0.0.1-SNAPSHOT.jar");

        final ByteBuffer jarbuf;
        try (FileChannel jarch = FileChannel.open(jarfile.toPath(), StandardOpenOption.READ)) {
            final int jarsz = (int) jarch.size();
            jarbuf = ByteBuffer.allocate(jarsz);
            while (jarbuf.remaining() > 0) {
                jarch.read(jarbuf);
            }
            jarbuf.flip();
        } catch (final IOException iox) {
            throw new RuntimeException("cannot load jar", iox);
        }
        return jarbuf;
    }

    private void permitInvokeLambda(final String accountId, final CreateRestApiResult createApiResult,
            final CreateFunctionResult function) {
        final String sourceArn = "arn:aws:execute-api:us-east-1:" + accountId + ":" + createApiResult.getId() + "/*/"
                + HttpMethod + "/" + Path;
        System.out.println(sourceArn);

        // allow api to invoke lambda function
        final AddPermissionRequest aprq = new AddPermissionRequest().withFunctionName(function.getFunctionName())
                .withAction("lambda:InvokeFunction")
                .withPrincipal("apigateway.amazonaws.com")
                .withStatementId("sid" + Math.abs(rng.nextLong()))
                .withSourceArn(sourceArn);
        this.awsLambdaClient.addPermission(aprq);

    }

    private CreateDeploymentResult stageApi(final CreateRestApiResult createApiResult) {
        final CreateDeploymentRequest cdrq = new CreateDeploymentRequest().withRestApiId(createApiResult.getId())
                .withStageName("advance");
        return this.awsApiClient.createDeployment(cdrq);
    }

}
