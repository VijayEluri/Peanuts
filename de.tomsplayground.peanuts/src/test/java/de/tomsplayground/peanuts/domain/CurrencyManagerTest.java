package de.tomsplayground.peanuts.domain;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

import org.junit.Test;

import de.tomsplayground.peanuts.domain.base.CurrencyManager;

public class CurrencyManagerTest {

	@Test
	public void testExchangeRateDmEuro() {
		CurrencyManager manager = new CurrencyManager();
		BigDecimal exchangeRate = manager.getExchangeRate(Currency.getInstance("EUR"),
			Currency.getInstance("DEM"));
		assertEquals(new BigDecimal("1.95583"), exchangeRate);
	}

	@Test
	public void testExchangeRateEuroDm() {
		CurrencyManager manager = new CurrencyManager();
		BigDecimal exchangeRate = manager.getExchangeRate(Currency.getInstance("DEM"),
			Currency.getInstance("EUR"));
		BigDecimal bigDecimal = new BigDecimal("1.95583");
		bigDecimal = BigDecimal.ONE.divide(bigDecimal, 10, RoundingMode.HALF_EVEN);
		assertEquals(bigDecimal, exchangeRate);
	}

}
