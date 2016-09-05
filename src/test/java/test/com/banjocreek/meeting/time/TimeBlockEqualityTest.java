package test.com.banjocreek.meeting.time;

import static org.junit.Assert.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.banjocreek.meeting.time.TimeBlock;

public class TimeBlockEqualityTest {

    TimeBlock control, copy;

    List<TimeBlock> different;

    @Before
    public void setup() {

        final Instant cstart = Instant.ofEpochMilli(1203350);
        final Duration cduration = Duration.ofMillis(25003);

        this.control = new TimeBlock(cstart, cduration);
        this.copy = new TimeBlock(cstart, cduration);

        // @formatter:off
        this.different = Arrays.asList(
                new TimeBlock(cstart.plus(Duration.ofSeconds(1)), cduration),
                new TimeBlock(cstart, cduration.plusMillis(-1))
        );
        // @formatter:on

    }

    @Test
    public void testCommute() {
        assertTrue(this.copy.equals(this.control));
    }

    @Test
    public void testHash() {
        assertEquals(this.control.hashCode(), this.copy.hashCode());
    }

    @Test
    public void testRef() {
        assertTrue(this.control.equals(this.control));
    }

    @Test
    public void testRepeatableHash() {
        assertEquals(this.control.hashCode(), this.control.hashCode());
    }

    @Test
    public void testUn() {
        this.different.forEach(d -> {
            assertFalse(this.control.equals(d));
            assertFalse(d.equals(this.control));
        });
    }

    @Test
    public void testValue() {
        assertTrue(this.control.equals(this.copy));
    }

}
