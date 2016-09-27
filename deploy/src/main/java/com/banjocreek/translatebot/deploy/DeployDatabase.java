package com.banjocreek.translatebot.deploy;

public class DeployDatabase {

    public static void main(final String[] args) {
        new DatabaseDeployer().deploy();
    }

}
