package de.tomsplayground.peanuts.domain.statistics;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import de.tomsplayground.peanuts.domain.process.IPrice;
import de.tomsplayground.peanuts.domain.process.IPriceProvider;
import de.tomsplayground.util.Day;

public class Volatility {

	private static final MathContext MC = new MathContext(16, RoundingMode.HALF_EVEN);

	private final static Logger log = LoggerFactory.getLogger(Volatility.class);

	public double calculateVolatility(IPriceProvider pp) {
		Day maxDate = pp.getMaxDate();
		Day minDate = maxDate.addYear(-1);
		ImmutableList<BigDecimal> values = pp.getPrices(minDate, maxDate).stream()
			.map(IPrice::getClose)
			.collect(ImmutableList.toImmutableList());
		return calculateVolatility(values);
	}

	public double calculateVolatility(ImmutableList<BigDecimal> values) {
		log.debug("Size: "+values.size());
		BigDecimal v1 = values.get(0);
		double yield[] = new double[values.size()-1];
		int i = 0;
		for (BigDecimal v2 : values.subList(1, values.size())) {
			yield[i] = Math.log(v2.divide(v1, MC).doubleValue());
			if (log.isDebugEnabled()) {
				log.debug("V1:"+v1+ " V2:"+v2+" Y:"+yield[i]);
			}
			i++;
			v1 = v2;
		}
		double avg = 0;
		for (double r : yield) {
			avg += r;
		}
		avg = avg / yield.length;
		log.debug("AVG: "+avg);
		double volatility = 0;
		for (double r : yield) {
			double d = (r - avg);
			volatility += (d*d);
		}
		volatility = Math.sqrt(volatility * 252 / (yield.length - 1));
		log.debug("Vola: "+volatility);
		return volatility;
	}

}
