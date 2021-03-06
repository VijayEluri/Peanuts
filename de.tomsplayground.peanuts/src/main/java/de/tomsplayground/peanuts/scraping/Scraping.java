package de.tomsplayground.peanuts.scraping;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.PrettyXmlSerializer;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.XPather;
import org.htmlcleaner.XPatherException;

import de.tomsplayground.peanuts.domain.base.Security;
import de.tomsplayground.peanuts.domain.process.Price;
import de.tomsplayground.peanuts.domain.process.PriceBuilder;
import de.tomsplayground.util.Day;

public class Scraping {

	public static final String SCRAPING_PREFIX = "scaping.";

	public static final String SCRAPING_URL = "scaping.url";

	public static final String[] XPATH_KEYS = new String[]{"open", "close", "high", "low", "date"};

	private final String scrapingUrl;
	private final Map<String, String> xpathMap = new HashMap<String, String>();

	private String result = "";

	public Scraping(Security security) {
		for (String key : XPATH_KEYS) {
			xpathMap.put(key, security.getConfigurationValue(SCRAPING_PREFIX+key));
		}
		scrapingUrl = security.getConfigurationValue(SCRAPING_URL);
	}

	public String getResult() {
		return result;
	}

	public Price execute() {
		Price price = null;
		if (StringUtils.isBlank(scrapingUrl)) {
			return price;
		}
		StringBuilder resultStr = new StringBuilder();
		try {
			HtmlCleaner htmlCleaner = new HtmlCleaner();
			TagNode tagNode = htmlCleaner.clean(new URL(scrapingUrl));

			PrettyXmlSerializer xmlSerializer = new PrettyXmlSerializer(htmlCleaner.getProperties());
			String string = xmlSerializer.getAsString(tagNode);

			PriceBuilder priceBuilder = new PriceBuilder();
			for (String key : XPATH_KEYS) {
				String xpath = xpathMap.get(key);
				if (StringUtils.isNotEmpty(xpath)) {
					XPather xPather = new XPather(xpath);
					Object[] result = xPather.evaluateAgainstNode(tagNode);
					for (Object object : result) {
						resultStr.append('>').append(object).append('\n');
					}
					if (result.length > 0) {
						String value = result[0].toString().trim();
						if (StringUtils.equals(key, "date")) {
							Day day = scrapDay(value);
							priceBuilder.setDay(day);
							resultStr.append(key).append(": ").append(day).append('\n');
						} else {
							BigDecimal p = scapBigDecimal(value);
							if (key.equals("open")) {
								priceBuilder.setOpen(p);
							}
							if (key.equals("close")) {
								priceBuilder.setClose(p);
							}
							if (key.equals("high")) {
								priceBuilder.setHigh(p);
							}
							if (key.equals("low")) {
								priceBuilder.setLow(p);
							}
							resultStr.append(key).append(": ").append(p).append('\n');
						}
					}
				}
			}
			price = priceBuilder.build();
			if (price.getClose().signum() == 0) {
				price = null;
			}
			resultStr.append("Price: ").append(price).append('\n');
			resultStr.append('\n').append(string);
		} catch (RuntimeException e) {
			resultStr.append(e.getMessage());
		} catch (IOException e) {
			resultStr.append(e.getMessage());
		} catch (XPatherException e) {
			resultStr.append(e.getMessage());
		} catch (ParseException e) {
			resultStr.append(e.getMessage());
		}
		result = resultStr.toString();
		return price;
	}

	private static String removeTrailingText(String value) {
		int i = StringUtils.indexOfAnyBut(value, "0123456789,.");
		if (i != -1) {
			value = value.substring(0, i);
		}
		return value;
	}

	public static Day scrapDay(String value) throws ParseException {
		value = removeTrailingText(value);
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
		Day date = Day.fromDate((dateFormat.parse(value)));
		return date;
	}

	public static BigDecimal scapBigDecimal(String value) {
		value = removeTrailingText(value);
		if (value.indexOf(',') != -1 && value.indexOf('.') != -1) {
			// ',' and '.' => convert
			if (StringUtils.lastIndexOf(value, ',') > StringUtils.lastIndexOf(value, '.')) {
				value = StringUtils.remove(value, '.');
				value = value.replace(',', '.');
			} else {
				value = StringUtils.remove(value, ',');
			}
		} else if (value.indexOf(',') != -1) {
			// only ',' => convert to '.'
			value = value.replace(',', '.');
		}
		return new BigDecimal(value);
	}
}
