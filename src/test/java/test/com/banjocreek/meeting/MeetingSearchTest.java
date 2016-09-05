package test.com.banjocreek.meeting;

import static org.junit.Assert.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;

public class MeetingSearchTest {

    @Test
    public void testFindsEarliestCommonFreeTime() {

        // Given
        final Instant earliest = Instant.ofEpochMilli(12345);
        final Duration meetingLength = Duration.ofMinutes(30);

        final List<Freetime> ft1 = IntStream.range(1, 10)
                .mapToObj(i -> new Freetime(earliest.plus(Duration.ofMinutes(i * 90)), Duration.ofMinutes(60)))
                .collect(Collectors.toList());

        final List<Freetime> ft2 = IntStream.range(3, 10)
                .mapToObj(i -> new Freetime(earliest.plus(Duration.ofMinutes(i * 40)), Duration.ofMinutes(30)))
                .collect(Collectors.toList());

        // when
        final Optional<Meeting> actual = search(meetingLength, ft1, ft2);

        // then
        final Optional<Meeting> expected = Optional
                .of(new Meeting(earliest.plus(Duration.ofMinutes(120)), Duration.ofMinutes(30)));

        assertEquals(expected, actual);
    }

    private static Optional<Meeting> maybeMeet(Duration l, Freetime a, Freetime b) {

        long begin = Math.max(a.start.toEpochMilli(), b.start.toEpochMilli());
        long end = Math.min(a.start.plus(a.length).toEpochMilli(), b.start.plus(b.length).toEpochMilli());

        if (l.toMillis() >= end - begin)
            return Optional.of(new Meeting(Instant.ofEpochMilli(begin), l));
        else
            return Optional.empty();
    }

    private static Meeting earliest(Meeting a, Meeting b) {
        return a.start.isBefore(b.start) ? a : b;
    }

    public static Optional<Meeting> search(Duration length, Collection<Freetime> a, Collection<Freetime> b) {

        final ArrayList<Meeting> candidates = new ArrayList<>();

        a.forEach(fa -> b.forEach(fb -> {
            maybeMeet(length, fa, fb).ifPresent(candidates::add);
        }));

        return candidates.stream().reduce(MeetingSearchTest::earliest);
      
    }

}

class Freetime {

    public Instant start;
    public Duration length;

    public Freetime(Instant start, Duration length) {
        super();
        this.start = start;
        this.length = length;
    }

}

class Meeting {

    public Instant start;
    public Duration length;

    public Meeting(Instant start, Duration length) {
        super();
        this.start = start;
        this.length = length;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Meeting && eq((Meeting)obj);
    }

    private boolean eq(Meeting other) {
        return this.start.equals(other.start) && this.length.equals(other.length);
    }
    
}
