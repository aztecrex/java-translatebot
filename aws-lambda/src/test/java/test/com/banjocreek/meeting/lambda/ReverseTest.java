package test.com.banjocreek.meeting.lambda;

import static org.junit.Assert.*;

import org.junit.Test;

import com.banjocreek.meeting.lambda.Reverse;

public class ReverseTest {

    @Test
    public void testEcho() {

        // Given
        final Reverse echo = new Reverse();
        final String in = "Test case Bravo";

        // When
        final String actual = echo.apply(in);

        // Then
        final String expected = new StringBuilder(in).reverse().toString();
        assertEquals(expected, actual);
    }

}
