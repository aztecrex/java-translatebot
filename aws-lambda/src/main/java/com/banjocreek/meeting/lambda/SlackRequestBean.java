package com.banjocreek.meeting.lambda;

/**
 * Yeah, it's a bean. It's mutable. I'm over it now.
 */
public final class SlackRequestBean {

    /*
     * Get this: the lambda de-serializer doesn't know nothin' about Java
     * naming. Underscores.
     */
    private String channel_id;
    private String command;
    private String team_domain;
    private String team_id;
    private String text;
    private String timestamp;
    private String token;
    private String trigger_word;
    private String user_id;
    private String user_name;

    public String getChannel_id() {
        return this.channel_id;
    }

    public String getCommand() {
        return this.command;
    }

    public String getTeam_domain() {
        return this.team_domain;
    }

    public String getTeam_id() {
        return this.team_id;
    }

    public String getText() {
        return this.text;
    }

    public String getTimestamp() {
        return this.timestamp;
    }

    public String getToken() {
        return this.token;
    }

    public String getTrigger_word() {
        return this.trigger_word;
    }

    public String getUser_id() {
        return this.user_id;
    }

    public String getUser_name() {
        return this.user_name;
    }

    public void setChannel_id(final String channel_id) {
        this.channel_id = channel_id;
    }

    public void setCommand(final String command) {
        this.command = command;
    }

    public void setTeam_domain(final String team_domain) {
        this.team_domain = team_domain;
    }

    public void setTeam_id(final String team_id) {
        this.team_id = team_id;
    }

    public void setText(final String text) {
        this.text = text;
    }

    public void setTimestamp(final String timestamp) {
        this.timestamp = timestamp;
    }

    public void setToken(final String token) {
        this.token = token;
    }

    public void setTrigger_word(final String trigger_word) {
        this.trigger_word = trigger_word;
    }

    public void setUser_id(final String user_id) {
        this.user_id = user_id;
    }

    public void setUser_name(final String user_name) {
        this.user_name = user_name;
    }

}
