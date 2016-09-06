package com.banjocreek.meeting.lambda;

import java.util.function.Function;

public final class Reverse implements Function<String, String> {

    @Override
    public String apply(final String t) {
        return new StringBuilder(t).reverse().toString();
    }

}
