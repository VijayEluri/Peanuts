package de.tomsplayground.peanuts.app.yahoo;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

public class YahooSecuritySearcherTest {

	@Test
	@Ignore
	public void simpleTest() {
		YahooSecuritySearcher yahooSecuritySearcher = new YahooSecuritySearcher();
		List<YahooSecurity> search = yahooSecuritySearcher.search("US0394831020");
		assertFalse(search.isEmpty());
	}
}
