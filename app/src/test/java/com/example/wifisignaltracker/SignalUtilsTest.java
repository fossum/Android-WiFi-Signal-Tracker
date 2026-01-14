package com.example.wifisignaltracker;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;

public class SignalUtilsTest {

    @Test
    public void testSortSsids_CaseInsensitive() {
        List<String> input = Arrays.asList("beta", "Alpha", "charlie", "Delta");
        List<String> expected = Arrays.asList("Alpha", "beta", "charlie", "Delta");

        List<String> result = SignalUtils.sortSsids(input);

        assertEquals(expected, result);
    }

    @Test
    public void testSortSsids_WithNumbers() {
        List<String> input = Arrays.asList("b", "A", "1");
        List<String> expected = Arrays.asList("1", "A", "b");

        List<String> result = SignalUtils.sortSsids(input);
        assertEquals(expected, result);
    }
}
