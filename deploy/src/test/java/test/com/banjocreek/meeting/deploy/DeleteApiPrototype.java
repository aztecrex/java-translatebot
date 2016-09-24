package test.com.banjocreek.meeting.deploy;

import java.util.function.Predicate;

import com.amazonaws.services.apigateway.AmazonApiGatewayAsyncClient;
import com.amazonaws.services.apigateway.model.DeleteRestApiRequest;
import com.amazonaws.services.apigateway.model.GetRestApisRequest;
import com.amazonaws.services.apigateway.model.GetRestApisResult;
import com.amazonaws.services.apigateway.model.RestApi;

public class DeleteApiPrototype {

    public static void main(final String[] args) {

        final AmazonApiGatewayAsyncClient client = new AmazonApiGatewayAsyncClient();

        final GetRestApisRequest graprq = new GetRestApisRequest();

        final GetRestApisResult graprs = client.getRestApis(graprq);

        graprs.getItems()
                .stream()
                .filter(eqname("ChatMeeting"))
                .map(RestApi::getId)
                .map(id -> new DeleteRestApiRequest().withRestApiId(id))
                .forEach(client::deleteRestApi);

    }

    private static Predicate<RestApi> eqname(final String name) {
        return ra -> ra.getName().equals(name);
    }
}
