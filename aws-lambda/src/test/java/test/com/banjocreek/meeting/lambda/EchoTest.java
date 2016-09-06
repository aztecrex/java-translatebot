package test.com.banjocreek.meeting.lambda;

import static org.junit.Assert.*;

import org.junit.Test;

import com.banjocreek.meeting.lambda.Echo;

public class EchoTest {

    @Test
    public void testEcho() {

        // Given
        final Echo echo = new Echo();
        final String in = "Test case Bravo";

        // When
        final String actual = echo.apply(in);
        assertEquals(in, actual);
    }

}
