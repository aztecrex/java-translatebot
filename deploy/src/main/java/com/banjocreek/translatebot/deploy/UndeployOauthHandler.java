package com.banjocreek.translatebot.deploy;

public class UndeployOauthHandler {

    public static void main(final String[] args) {
        new OauthHandlerDeployer().undeploy();
    }

}
