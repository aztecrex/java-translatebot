package com.banjocreek.meeting.lambda;

import java.util.Collections;
import java.util.Map;

public final class SlackMapInfoHandler {

    public Map<String, String> handle(final Map<String, String> request) {

        final StringBuilder buf = new StringBuilder();

        request.entrySet().stream().forEach(e -> {
            buf.append(e.getKey()).append(": '").append(e.getValue()).append("'\n");
        });

        return Collections.singletonMap("text", buf.toString());
    }

}
