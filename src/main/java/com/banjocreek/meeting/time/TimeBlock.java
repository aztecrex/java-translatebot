package com.banjocreek.meeting.time;

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
            fa.intersect(fb).flatMap(blk -> blk.fit(length)).ifPresent(candidates::add);
        }));

        return candidates.stream().reduce(TimeBlock::earliest);

    }

    private static TimeBlock earliest(final TimeBlock a, final TimeBlock b) {
        return a.start().isBefore(b.start()) ? a : b;
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

    public Optional<TimeBlock> fit(final Duration length) {
        return this.duration.compareTo(length) >= 0 ? Optional.of(new TimeBlock(this.start, length)) : Optional.empty();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.start, this.duration);
    }

    public Optional<TimeBlock> intersect(final TimeBlock other) {

        // assumes positive duration

        final Instant begin = this.start.isBefore(other.start) ? other.start : this.start;
        final Instant end = end().isBefore(other.end()) ? end() : other.end();

        final Duration available = Duration.between(begin, end);

        return Optional.of(available).filter(d -> !d.isNegative() && !d.isZero()).map(d -> new TimeBlock(begin, d));

    }

    public Instant start() {
        return this.start;
    }

    private Instant end() {

        // assumes positive duration

        return this.start.plus(this.duration);

    }

    private boolean eq(final TimeBlock other) {
        return this.start.equals(other.start) && this.duration.equals(other.duration);
    }

}