package de.tomsplayground.peanuts.domain.process;

import java.math.BigDecimal;

public class NoTrailingStrategy implements ITrailingStrategy {

	@Override
	public BigDecimal calculateStop(BigDecimal stop, IPrice price) {
		return stop;
	}

}
