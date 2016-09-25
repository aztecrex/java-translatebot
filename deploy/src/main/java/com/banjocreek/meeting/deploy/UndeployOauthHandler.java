package com.banjocreek.meeting.deploy;

public class UndeployOauthHandler {

    public static void main(final String[] args) {
        new OauthHandlerDeployer().undeploy();
    }

}
