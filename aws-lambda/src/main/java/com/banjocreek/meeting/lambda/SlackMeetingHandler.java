package com.banjocreek.meeting.lambda;

import java.util.function.Function;

public final class SlackMeetingHandler {

    private final Function<String, String> process = new Reverse();

    public SlackResponseBean handle(final SlackRequestBean request) {

        // @formatter:off
        final String msg = new StringBuilder()

        .append("channelId").append(": ").append(request.getChannel_id()).append("\n")
        .append("teamDomain").append(": ").append(request.getTeam_domain()).append("\n")
        .append("teamId").append(": ").append(request.getTeam_id()).append("\n")
        .append("text").append(": ").append(request.getText()).append("\n")
        .append("timestamp").append(": ").append(request.getTimestamp()).append("\n")
        .append("token").append(": ").append(request.getToken()).append("\n")
        .append("triggerWord").append(": ").append(request.getTrigger_word()).append("\n")
        .append("command").append(": ").append(request.getCommand()).append("\n")
        .append("userId").append(": ").append(request.getUser_id()).append("\n")
        .append("userName").append(": ").append(request.getUser_name()).append("\n")

        .append("result").append(": ").append(this.process.apply(request.getText()))

        .toString();
        // @formatter:on

        final SlackResponseBean response = new SlackResponseBean();
        response.setText(msg);
        return response;
    }

}
