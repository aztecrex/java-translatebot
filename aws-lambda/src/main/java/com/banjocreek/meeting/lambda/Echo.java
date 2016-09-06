package com.banjocreek.meeting.lambda;

import java.util.function.Function;

public final class Echo implements Function<String, String> {

    @Override
    public String apply(final String t) {
        return t;
    }

}
