# Translator Slackbot

The Translator Slackbot translates messages between users' languages in
real time. The Slackbot is useful for teams that transcend native language
boundaries.

Translation services are provided by the Google Translate API.

## Features

* choose different languages per Slack channel
* auto-detects the source language and skips it in translated messages
* if authorized, modifies translated message with the corresponding translations
* if not authorized, posts translations as the bot user


## Add to Your Slack Team

<a href="https://slack.com/oauth/authorize?scope=commands,bot,chat:write:user,chat:write:bot&client_id=76454819904.83569102723"><img alt="Add to Slack" height="40" width="139" src="https://platform.slack-edge.com/img/add_to_slack.png" srcset="https://platform.slack-edge.com/img/add_to_slack.png 1x, https://platform.slack-edge.com/img/add_to_slack@2x.png 2x" /></a>

 Any team member can authorize the bot and allow it to modify their messags.
 
This is prototype code so it might not be available all the time.

After you add it to your team, invite the bot to a channel with __/invite @borges__ .
You will need to present your Google Translate API credentials using the
__/borges configure <auth-token>__ command only once per Slack team.

## Implementation

The iplementation is in Java and uses __AWS Lambda__, __AWS API Gateway__, __AWS DynamoDB__,
__AWS S3__, and __AWS Route53__ along with __Google Translate API__ .  Notice that
EC2 is not on the list. This application does not reqire any dedicated VM. All
logic is implemented in Lambda.

There are three handlers, one each for the OAuth flow, Slack events, and Slack slash
commands.

## Status

This is prototype all the way. Most of the time spent developing this application was
experimenting with the Lambda, API Gateway and Slack services and getting them to play
nicely together. So you will see a lot of duplicate logic and refactoring opportunities
in the source. Since it wasn't test driven, it needs to be redesigned using TDD to make
it production-ready.  Depending on the feedback I get, I will decide whether to
productionalize it.

## Run it Yourself

All the code to deploy to AWS is in the deploy sub-project. Be sure
to configure your workstation with the necessary AWS credentials. The easiest way to
do that is with the AWS CLI ```aws configure``` command.

You may have to mess with a code path and the domain name to get it running for yourself
but in broad strokes, run the main methods from these classes:

* DeployDatabase#main _provision a DynamoDB table_
* DeployCommandHandler#main _provision the Lambda Function and API Gateway for handling slash commands_
* DeployEventHandler#main _provision the Lambda Function and API Gateway for handling Slack events_
* DeployOauthHandler#main _provision the Lambda Funcation and API Gateway for the OAuth flows_
* DeployWebsite#main _provision bucket and Route53 domain, and upload web pages_


Enjoy
