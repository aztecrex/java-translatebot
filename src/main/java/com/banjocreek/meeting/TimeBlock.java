package com.banjocreek.meeting;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

public final class TimeBlock {

    public static Optional<TimeBlock> searchForCommon(final Duration length, final Collection<TimeBlock> a,
            final Collection<TimeBlock> b) {

        // Brute force

        final ArrayList<TimeBlock> candidates = new ArrayList<>();

        a.forEach(fa -> b.forEach(fb -> {
            maybeCommon(length, fa, fb).ifPresent(candidates::add);
        }));

        return candidates.stream().reduce(TimeBlock::earliest);

    }

    private static TimeBlock earliest(final TimeBlock a, final TimeBlock b) {
        return a.start().isBefore(b.start()) ? a : b;
    }

    private static Optional<TimeBlock> maybeCommon(final Duration l, final TimeBlock a, final TimeBlock b) {

        final long begin = Math.max(a.start().toEpochMilli(), b.start().toEpochMilli());
        final long end = Math.min(a.start().plus(a.duration()).toEpochMilli(),
                b.start().plus(b.duration()).toEpochMilli());

        if (l.toMillis() >= end - begin)
            return Optional.of(new TimeBlock(Instant.ofEpochMilli(begin), l));
        else
            return Optional.empty();
    }

    private final Duration duration;

    private final Instant start;

    public TimeBlock(final Instant start, final Duration duration) {
        this.start = start;
        this.duration = duration;
    }

    public Duration duration() {
        return this.duration;
    }

    @Override
    public boolean equals(final Object obj) {
        return this == obj || obj instanceof TimeBlock && eq((TimeBlock) obj);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.start, this.duration);
    }

    public Instant start() {
        return this.start;
    }

    private boolean eq(final TimeBlock other) {
        return this.start.equals(other.start) && this.duration.equals(other.duration);
    }

}