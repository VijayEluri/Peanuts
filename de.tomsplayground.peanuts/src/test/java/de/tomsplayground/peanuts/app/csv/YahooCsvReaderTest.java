package de.tomsplayground.peanuts.app.csv;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;

import junit.framework.TestCase;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import de.tomsplayground.peanuts.app.quicken.QifReaderTest;
import de.tomsplayground.peanuts.domain.process.IPrice;
import de.tomsplayground.util.Day;

public class YahooCsvReaderTest extends TestCase {

	@Test
	public void testRead() throws IOException {
		Reader in = new InputStreamReader(QifReaderTest.class.getResourceAsStream("/Yahoo.csv"));
		YahooCsvReader reader = new YahooCsvReader(in);
		IOUtils.closeQuietly(in);

		List<IPrice> prices = reader.getPrices();
		assertEquals(3, prices.size());
		IPrice price = prices.get(0);
		assertEquals(new BigDecimal("52.48"), price.getValue());
		assertEquals(new BigDecimal("53.59"), price.getOpen());
		assertEquals(new BigDecimal("53.60"), price.getHigh());
		assertEquals(new BigDecimal("52.42"), price.getLow());
		assertEquals(new BigDecimal("52.48"), price.getClose());
		Day date = price.getDay();
		assertEquals(new Day(2007, 4, 23), date);
	}
	
	@Test
	public void testEmpty() throws IOException {
		Reader in = new StringReader("");
		YahooCsvReader reader = new YahooCsvReader(in, YahooCsvReader.Type.CURRENT);
		
		List<IPrice> prices = reader.getPrices();
		assertEquals(0, prices.size());
	}
	
	@Test
	public void testReadCurrent2() throws IOException {
		Reader in = new InputStreamReader(QifReaderTest.class.getResourceAsStream("/Yahoo_current2.csv"));
		YahooCsvReader reader = new YahooCsvReader(in, YahooCsvReader.Type.CURRENT);
		IOUtils.closeQuietly(in);

		List<IPrice> prices = reader.getPrices();
		assertEquals(1, prices.size());
		assertEquals(new BigDecimal("106.25"), prices.get(0).getValue());
		assertEquals(new BigDecimal("101.90"), prices.get(0).getOpen());
		assertEquals(new BigDecimal("106.20"), prices.get(0).getHigh());
		assertEquals(new BigDecimal("101.75"), prices.get(0).getLow());
		assertEquals(new BigDecimal("106.25"), prices.get(0).getClose());
		Day date = prices.get(0).getDay();
		assertEquals(new Day(2011, 1, 1), date);
	}

}
