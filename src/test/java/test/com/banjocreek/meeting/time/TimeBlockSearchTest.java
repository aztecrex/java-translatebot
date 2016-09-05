package test.com.banjocreek.meeting.time;

import static org.junit.Assert.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;

import com.banjocreek.meeting.time.TimeBlock;

public class TimeBlockSearchTest {

    @Test
    public void testFindsEarliestCommonFreeTime() {

        // Given
        final Instant earliest = Instant.ofEpochMilli(12345);
        final Duration TimeBlockLength = Duration.ofMinutes(30);

        final List<TimeBlock> ft1 = IntStream.range(1, 10)
                .mapToObj(i -> new TimeBlock(earliest.plus(Duration.ofMinutes(i * 90)), Duration.ofMinutes(60)))
                .collect(Collectors.toList());

        final List<TimeBlock> ft2 = IntStream.range(3, 10)
                .mapToObj(i -> new TimeBlock(earliest.plus(Duration.ofMinutes(i * 40)), Duration.ofMinutes(30)))
                .collect(Collectors.toList());

        // When
        final Optional<TimeBlock> actual = TimeBlock.searchForCommon(TimeBlockLength, ft1, ft2);

        // Then
        final Optional<TimeBlock> expected = Optional
                .of(new TimeBlock(earliest.plus(Duration.ofMinutes(120)), Duration.ofMinutes(30)));

        assertEquals(expected, actual);
    }

}
