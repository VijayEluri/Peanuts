package de.tomsplayground.peanuts.client.editors.security;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Currency;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ICellModifier;
import org.eclipse.jface.viewers.ITableColorProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.EditorPart;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import de.tomsplayground.peanuts.app.fourtraders.FourTraders;
import de.tomsplayground.peanuts.app.morningstar.KeyRatios;
import de.tomsplayground.peanuts.client.app.Activator;
import de.tomsplayground.peanuts.client.widgets.CurrencyComboViewer;
import de.tomsplayground.peanuts.domain.base.Inventory;
import de.tomsplayground.peanuts.domain.base.InventoryEntry;
import de.tomsplayground.peanuts.domain.base.Security;
import de.tomsplayground.peanuts.domain.currenncy.CurrencyConverter;
import de.tomsplayground.peanuts.domain.currenncy.ExchangeRates;
import de.tomsplayground.peanuts.domain.fundamental.AvgFundamentalData;
import de.tomsplayground.peanuts.domain.fundamental.CurrencyAjustedFundamentalData;
import de.tomsplayground.peanuts.domain.fundamental.FundamentalData;
import de.tomsplayground.peanuts.domain.process.IPriceProvider;
import de.tomsplayground.peanuts.domain.process.PriceProviderFactory;
import de.tomsplayground.peanuts.domain.process.StockSplit;
import de.tomsplayground.peanuts.util.PeanutsUtil;
import de.tomsplayground.util.Day;

public class FundamentalDataEditorPart extends EditorPart {

	private static final MathContext MC = new MathContext(10, RoundingMode.HALF_EVEN);

	private static final BigDecimal DEPT_LIMIT = new BigDecimal("1.0");
	private static final BigDecimal DIVIDENDE_LIMIT = new BigDecimal("0.9");

	private TableViewer tableViewer;
	private final int colWidth[] = new int[15];
	private boolean dirty = false;
	private List<FundamentalData> fundamentalDatas;
	private IPriceProvider priceProvider;
	private InventoryEntry inventoryEntry;
	private CurrencyComboViewer currencyComboViewer;
	private CurrencyConverter currencyConverter;
	private List<Object> tableRows;

	private class FundamentalDataTableLabelProvider extends LabelProvider implements ITableLabelProvider, ITableColorProvider {

		@Override
		public Image getColumnImage(Object element, int columnIndex) {
			if (columnIndex == 0) {
				if (element instanceof FundamentalData) {
					FundamentalData data = (FundamentalData)element;
					if (data.isLocked()) {
						return Activator.getDefault().getImage("icons/lock.png");
					}
				}
			}
			return null;
		}

		private FundamentalData getPreviousYear(FundamentalData data) {
			final int prevYear = data.getYear() -1;
			return Iterables.find(fundamentalDatas, new Predicate<FundamentalData>() {
				@Override
				public boolean apply(FundamentalData arg0) {
					return arg0.getYear() == prevYear;
				}
			}, null);
		}

		private BigDecimal growth(BigDecimal now, BigDecimal prev) {
			if (prev.signum() == -1 && now.signum() == 1) {
				return BigDecimal.ZERO;
			}
			if (prev.signum() != 0) {
				return now.subtract(prev).divide(prev.abs(), MC);
			}
			return BigDecimal.ZERO;
		}

		private BigDecimal epsGrowth(FundamentalData data) {
			FundamentalData previousYearData = getPreviousYear(data);
			if (previousYearData != null) {
				BigDecimal eps = data.getEarningsPerShare();
				BigDecimal prevEps = previousYearData.getEarningsPerShare();
				return growth(eps, prevEps);
			}
			return BigDecimal.ZERO;
		}

		private BigDecimal divGrowth(FundamentalData data) {
			FundamentalData previousYearData = getPreviousYear(data);
			if (previousYearData != null) {
				BigDecimal div = data.getDividende();
				BigDecimal prevDiv = previousYearData.getDividende();
				return growth(div, prevDiv);
			}
			return BigDecimal.ZERO;
		}

		private BigDecimal adjustedEpsGrowth(FundamentalData data) {
			FundamentalData previousYearData = getPreviousYear(data);
			if (previousYearData != null) {
				BigDecimal eps = currencyAdjustedEPS(data);
				BigDecimal prevEps = currencyAdjustedEPS(previousYearData);
				return growth(eps, prevEps);
			}
			return BigDecimal.ZERO;
		}

		private BigDecimal adjustedDivGrowth(FundamentalData data) {
			FundamentalData previousYearData = getPreviousYear(data);
			if (previousYearData != null) {
				BigDecimal div = currencyAdjustedDiv(data);
				BigDecimal prevDiv = currencyAdjustedDiv(previousYearData);
				if (prevDiv.signum() != 0) {
					return div.divide(prevDiv, MC).subtract(BigDecimal.ONE);
				}
			}
			return BigDecimal.ZERO;
		}

		private BigDecimal currencyAdjustedEPS(FundamentalData data) {
			if (currencyConverter != null) {
				CurrencyAjustedFundamentalData currencyAjustedData = new CurrencyAjustedFundamentalData(data, currencyConverter);
				return currencyAjustedData.getEarningsPerShare();
			}
			return data.getEarningsPerShare();
		}

		private BigDecimal currencyAdjustedDiv(FundamentalData data) {
			if (currencyConverter != null) {
				CurrencyAjustedFundamentalData currencyAjustedData = new CurrencyAjustedFundamentalData(data, currencyConverter);
				return currencyAjustedData.getDividende();
			}
			return data.getDividende();
		}

		@Override
		public String getColumnText(Object element, int columnIndex) {
			if (element instanceof AvgFundamentalData) {
				// Fresh data
				AvgFundamentalData data = new AvgFundamentalData(fundamentalDatas, priceProvider, currencyConverter);
				switch (columnIndex) {
					case 0:
						return "Avg";
					case 7:
						BigDecimal avgEpsGrowth = data.getAvgEpsGrowth();
						if (avgEpsGrowth == null) {
							return "";
						}
						return PeanutsUtil.formatPercent(avgEpsGrowth.subtract(BigDecimal.ONE));
					case 9:
						BigDecimal currencyAdjustedAvgEpsGrowth = data.getCurrencyAdjustedAvgEpsGrowth();
						if (currencyAdjustedAvgEpsGrowth == null) {
							return "";
						}
						return PeanutsUtil.formatPercent(currencyAdjustedAvgEpsGrowth.subtract(BigDecimal.ONE));
					case 11:
						return PeanutsUtil.format(data.getAvgPE(), 1);
					default:
						return "";
				}
			} else {
				FundamentalData data = (FundamentalData) element;
				switch (columnIndex) {
					case 0:
						return String.valueOf(data.getYear());
					case 1:
						return String.valueOf(data.getFicalYearEndsMonth());
					case 2:
						return PeanutsUtil.formatCurrency(data.getDividende(), null);
					case 3:
						return PeanutsUtil.formatPercent(divGrowth(data));
					case 4:
						return PeanutsUtil.formatCurrency(currencyAdjustedDiv(data), null);
					case 5:
						return PeanutsUtil.formatPercent(adjustedDivGrowth(data));
					case 6:
						return PeanutsUtil.formatCurrency(data.getEarningsPerShare(), null);
					case 7:
						return PeanutsUtil.formatPercent(epsGrowth(data));
					case 8:
						return PeanutsUtil.formatCurrency(currencyAdjustedEPS(data), null);
					case 9:
						return PeanutsUtil.formatPercent(adjustedEpsGrowth(data));
					case 10:
						return PeanutsUtil.formatQuantity(data.getDebtEquityRatio());
					case 11:
						if (currencyConverter != null) {
							CurrencyAjustedFundamentalData currencyAjustedData = new CurrencyAjustedFundamentalData(data, currencyConverter);
							return PeanutsUtil.format(currencyAjustedData.calculatePeRatio(priceProvider), 1);
						}
						return PeanutsUtil.format(data.calculatePeRatio(priceProvider), 1);
					case 12:
						if (currencyConverter != null) {
							CurrencyAjustedFundamentalData currencyAjustedData = new CurrencyAjustedFundamentalData(data, currencyConverter);
							return PeanutsUtil.formatPercent(currencyAjustedData.calculateDivYield(priceProvider));
						}
						return PeanutsUtil.formatPercent(data.calculateDivYield(priceProvider));
					case 13:
						if (inventoryEntry != null && data.getYear() == (new Day()).year) {
							if (currencyConverter != null) {
								CurrencyAjustedFundamentalData currencyAjustedData = new CurrencyAjustedFundamentalData(data, currencyConverter);
								return PeanutsUtil.formatPercent(currencyAjustedData.calculateYOC(inventoryEntry));
							}
							return PeanutsUtil.formatPercent(data.calculateYOC(inventoryEntry));
						} else {
							return "";
						}
					case 14:
						DateTime lastModifyDate = data.getLastModifyDate();
						if (lastModifyDate != null) {
							return DateTimeFormat.shortDateTime().print(lastModifyDate);
						}
						return "";
					default:
						return "";
				}
			}
		}
		@Override
		public String getText(Object element) {
			if (element instanceof FundamentalData) {
				FundamentalData data = (FundamentalData) element;
				return String.valueOf(data.getYear());
			}
			return "";
		}

		@Override
		public Color getBackground(Object element, int columnIndex) {
			if (element instanceof FundamentalData) {
				FundamentalData data = (FundamentalData) element;
				if (data.isIgnoreInAvgCalculation()) {
					return Activator.getDefault().getColorProvider().get(Activator.INACTIVE_ROW);
				} else if (columnIndex == 2) {
					BigDecimal dividende = data.getDividende();
					BigDecimal earningsPerShare = data.getEarningsPerShare();
					if (dividende.compareTo(BigDecimal.ZERO) > 0) {
						if (earningsPerShare.signum() <= 0) {
							return Activator.getDefault().getColorProvider().get(Activator.RED_BG);
						} else {
							BigDecimal ratio = dividende.divide(earningsPerShare, MC);
							if (ratio.compareTo(DIVIDENDE_LIMIT) > 0) {
								return Activator.getDefault().getColorProvider().get(Activator.RED_BG);
							}
						}
					}
				} else if (columnIndex == 6) {
					BigDecimal earningsPerShare = data.getEarningsPerShare();
					if (earningsPerShare.signum() < 0) {
						return Activator.getDefault().getColorProvider().get(Activator.RED_BG);
					}
				} else if (columnIndex == 10) {
					BigDecimal debtEquityRatio = data.getDebtEquityRatio();
					if (debtEquityRatio.compareTo(DEPT_LIMIT) >= 0) {
						return Activator.getDefault().getColorProvider().get(Activator.RED_BG);
					}
				}
			}
			return null;
		}

		@Override
		public Color getForeground(Object element, int columnIndex) {
			if (element instanceof FundamentalData) {
				FundamentalData data = (FundamentalData) element;
				if (columnIndex == 3) {
					if (divGrowth(data).signum() == -1) {
						return Activator.getDefault().getColorProvider().get(Activator.RED);
					}
				}
				if (columnIndex == 5) {
					if (adjustedDivGrowth(data).signum() == -1) {
						return Activator.getDefault().getColorProvider().get(Activator.RED);
					}
				}
				if (columnIndex == 7) {
					if (epsGrowth(data).signum() == -1) {
						return Activator.getDefault().getColorProvider().get(Activator.RED);
					}
				}
				if (columnIndex == 9) {
					if (adjustedEpsGrowth(data).signum() == -1) {
						return Activator.getDefault().getColorProvider().get(Activator.RED);
					}
				}
				FundamentalData currentFundamentalData = getSecurity().getFundamentalDatas().getCurrentFundamentalData();
				if (currentFundamentalData != null && currentFundamentalData.getYear() == data.getYear()) {
					return Activator.getDefault().getColorProvider().get(Activator.ACTIVE_ROW);
				}
			}
			return null;
		}
	}

	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
		if (!(input instanceof SecurityEditorInput)) {
			throw new PartInitException("Invalid Input: Must be SecurityEditorInput");
		}
		setSite(site);
		setInput(input);
		setPartName(input.getName());
	}

	@Override
	public void createPartControl(Composite parent) {
		final Security security = getSecurity();

		Composite top = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		top.setLayout(layout);

		Composite metaComposite = new Composite(top, SWT.NONE);
		metaComposite.setLayout(new GridLayout(5, false));
		currencyComboViewer = new CurrencyComboViewer(metaComposite, false);
		new Label(metaComposite, SWT.NONE).setText("Morningstar symbol:");
		final Text morningstarSymbol = new Text(metaComposite, SWT.NONE);
		morningstarSymbol.setText(security.getMorningstarSymbol());
		morningstarSymbol.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				security.setMorningstarSymbol(morningstarSymbol.getText());
				markDirty();
			}
		});
		GridData layoutData = new GridData();
		layoutData.widthHint = 100;
		morningstarSymbol.setLayoutData(layoutData);
		Button morningStartSymbolGo = new Button(metaComposite, SWT.PUSH);
		morningStartSymbolGo.setText("Load data");
		morningStartSymbolGo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				try {
					updateFundamentaData(new KeyRatios().readUrl(morningstarSymbol.getText()));
				} catch (RuntimeException ex) {
					ex.printStackTrace();
				}
			}
		});

		Button fourTradersGo = new Button(metaComposite, SWT.PUSH);
		fourTradersGo.setText("Load data from 4-Traders");
		fourTradersGo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				update4TradersData(security);
			}
		});

		tableViewer = new TableViewer(top, SWT.FULL_SELECTION | SWT.MULTI);
		Table table = tableViewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);

		int colNumber = 0;
		TableColumn col = new TableColumn(table, SWT.LEFT);
		col.setText("Year");
		col.setWidth((colWidth[colNumber] > 0) ? colWidth[colNumber] : 80);
		col.setResizable(true);
		ViewerComparator comparator = new ViewerComparator() {
			@Override
			public int category(Object element) {
				if (element instanceof AvgFundamentalData) {
					return 99;
				}
				return 0;
			}
			@Override
			public boolean isSorterProperty(Object element, String property) {
				return "year".equals(property);
			}
		};
		tableViewer.setComparator(comparator);
		table.setSortColumn(col);
		table.setSortDirection(SWT.UP);
		colNumber++;

		col = new TableColumn(table, SWT.RIGHT);
		col.setText("Fiscal Year");
		col.setWidth((colWidth[colNumber] > 0) ? colWidth[colNumber] : 40);
		col.setResizable(true);
		colNumber++;

		col = new TableColumn(table, SWT.RIGHT);
		col.setText("Dividende");
		col.setWidth((colWidth[colNumber] > 0) ? colWidth[colNumber] : 70);
		col.setResizable(true);
		colNumber++;

		col = new TableColumn(table, SWT.RIGHT);
		col.setText("Change %");
		col.setWidth((colWidth[colNumber] > 0) ? colWidth[colNumber] : 70);
		col.setResizable(true);
		colNumber++;

		col = new TableColumn(table, SWT.RIGHT);
		col.setText("Dividende "+security.getCurrency().getSymbol());
		col.setWidth((colWidth[colNumber] > 0) ? colWidth[colNumber] : 70);
		col.setResizable(true);
		colNumber++;

		col = new TableColumn(table, SWT.RIGHT);
		col.setText("Change %");
		col.setWidth((colWidth[colNumber] > 0) ? colWidth[colNumber] : 70);
		col.setResizable(true);
		colNumber++;

		col = new TableColumn(table, SWT.RIGHT);
		col.setText("EPS");
		col.setWidth((colWidth[colNumber] > 0) ? colWidth[colNumber] : 100);
		col.setResizable(true);
		colNumber++;

		col = new TableColumn(table, SWT.RIGHT);
		col.setText("Change %");
		col.setWidth((colWidth[colNumber] > 0) ? colWidth[colNumber] : 70);
		col.setResizable(true);
		colNumber++;

		col = new TableColumn(table, SWT.RIGHT);
		col.setText("EPS "+security.getCurrency().getSymbol());
		col.setWidth((colWidth[colNumber] > 0) ? colWidth[colNumber] : 70);
		col.setResizable(true);
		colNumber++;

		col = new TableColumn(table, SWT.RIGHT);
		col.setText("Change %");
		col.setWidth((colWidth[colNumber] > 0) ? colWidth[colNumber] : 70);
		col.setResizable(true);
		colNumber++;

		col = new TableColumn(table, SWT.RIGHT);
		col.setText("D/E ratio");
		col.setWidth((colWidth[colNumber] > 0) ? colWidth[colNumber] : 100);
		col.setResizable(true);
		colNumber++;

		col = new TableColumn(table, SWT.RIGHT);
		col.setText("P/E ratio");
		col.setWidth((colWidth[colNumber] > 0) ? colWidth[colNumber] : 70);
		col.setResizable(true);
		colNumber++;

		col = new TableColumn(table, SWT.RIGHT);
		col.setText("Div yield");
		col.setWidth((colWidth[colNumber] > 0) ? colWidth[colNumber] : 70);
		col.setResizable(true);
		colNumber++;

		col = new TableColumn(table, SWT.RIGHT);
		col.setText("YOC");
		col.setWidth((colWidth[colNumber] > 0) ? colWidth[colNumber] : 70);
		col.setResizable(true);
		colNumber++;

		col = new TableColumn(table, SWT.RIGHT);
		col.setText("Date");
		col.setWidth((colWidth[colNumber] > 0) ? colWidth[colNumber] : 120);
		col.setResizable(true);
		colNumber++;

		tableViewer.setColumnProperties(new String[] { "year", "fiscalYear", "div", "divgr", "div2", "div2gr",
			"EPS", "EPSgr", "EPS2", "EPS2gr", "deRatio", "peRatio", "divYield", "YOC", "date"});
		tableViewer.setCellModifier(new ICellModifier() {

			@Override
			public boolean canModify(Object element, String property) {
				return (element instanceof FundamentalData) && !((FundamentalData)element).isLocked() &&
					Lists.newArrayList("year", "fiscalYear", "div", "EPS", "deRatio").contains(property);
			}

			@Override
			public Object getValue(Object element, String property) {
				FundamentalData p = (FundamentalData) element;
				if (property.equals("year")) {
					return String.valueOf(p.getYear());
				} else if (property.equals("fiscalYear")) {
					return String.valueOf(p.getFicalYearEndsMonth());
				} else if (property.equals("div")) {
					return PeanutsUtil.formatCurrency(p.getDividende(), null);
				} else if (property.equals("EPS")) {
					return PeanutsUtil.formatCurrency(p.getEarningsPerShare(), null);
				} else if (property.equals("deRatio")) {
					return PeanutsUtil.formatCurrency(p.getDebtEquityRatio(), null);
				}
				return null;
			}

			@Override
			public void modify(Object element, String property, Object value) {
				FundamentalData p = (FundamentalData) ((TableItem) element).getData();
				try {
					if (property.equals("year")) {
						Integer newYear = Integer.valueOf((String) value);
						if (newYear.intValue() != p.getYear()) {
							p.setYear(newYear.intValue());
							p.updateLastModifyDate();
							tableViewer.update(p, new String[]{property});
							markDirty();
						}
					} else if (property.equals("fiscalYear")) {
						Integer newFiscalYear = Integer.valueOf((String) value);
						if (newFiscalYear.intValue() != p.getFicalYearEndsMonth()) {
							p.setFicalYearEndsMonth(newFiscalYear.intValue());
							p.updateLastModifyDate();
							tableViewer.update(p, new String[]{property});
							markDirty();
						}
					} else if (property.equals("div")) {
						BigDecimal v = PeanutsUtil.parseCurrency((String) value);
						if (v.compareTo(p.getDividende()) != 0) {
							p.setDividende(v);
							p.updateLastModifyDate();
							tableViewer.update(p, new String[]{property});
							markDirty();
						}
					} else if (property.equals("EPS")) {
						BigDecimal v = PeanutsUtil.parseCurrency((String) value);
						if (v.compareTo(p.getEarningsPerShare()) != 0) {
							p.setEarningsPerShare(v);
							p.updateLastModifyDate();
							tableViewer.update(p, new String[]{property});
							markDirty();
						}
					} else if (property.equals("deRatio")) {
						BigDecimal v = PeanutsUtil.parseCurrency((String) value);
						if (v.compareTo(p.getDebtEquityRatio()) != 0) {
							p.setDebtEquityRatio(v);
							p.updateLastModifyDate();
							tableViewer.update(p, new String[]{property});
							markDirty();
						}
					}
				} catch (ParseException | NumberFormatException e) {
					// Okay
				}
			}
		});
		tableViewer.setCellEditors(new CellEditor[] {new TextCellEditor(table), new TextCellEditor(table), new TextCellEditor(table),
			new TextCellEditor(table), new TextCellEditor(table), new TextCellEditor(table), new TextCellEditor(table),
			new TextCellEditor(table), new TextCellEditor(table), new TextCellEditor(table), new TextCellEditor(table),
			new TextCellEditor(table)});

		tableViewer.setLabelProvider(new FundamentalDataTableLabelProvider());
		tableViewer.setContentProvider(new ArrayContentProvider());
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		ImmutableList<StockSplit> stockSplits = Activator.getDefault().getAccountManager().getStockSplits(security);
		priceProvider = PriceProviderFactory.getInstance().getAdjustedPriceProvider(security, stockSplits);

		Inventory inventory = Activator.getDefault().getAccountManager().getFullInventory();
		for (InventoryEntry entry : inventory.getEntries()) {
			if (entry.getSecurity().equals(security)) {
				inventoryEntry = entry;
			}
		}

		fundamentalDatas = cloneFundamentalData(security.getFundamentalDatas().getDatas());
		if (! fundamentalDatas.isEmpty()) {
			Currency currency = fundamentalDatas.get(0).getCurrency();
			currencyComboViewer.selectCurrency(currency);
			ExchangeRates exchangeRate = Activator.getDefault().getExchangeRate();
			currencyConverter = exchangeRate.createCurrencyConverter(currency, security.getCurrency());
		}

		tableRows = new ArrayList<Object>();
		tableRows.addAll(fundamentalDatas);
		tableRows.add(new AvgFundamentalData(fundamentalDatas, priceProvider, currencyConverter));
		tableViewer.setInput(tableRows);

		currencyComboViewer.getCombo().addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Currency selectedCurrency = currencyComboViewer.getSelectedCurrency();
				if (selectedCurrency.equals(security.getCurrency())) {
					currencyConverter = null;
				} else {
					ExchangeRates exchangeRate = Activator.getDefault().getExchangeRate();
					currencyConverter = exchangeRate.createCurrencyConverter(selectedCurrency, security.getCurrency());
				}
				tableViewer.refresh();
				markDirty();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				markDirty();
			}
		});

		MenuManager menuManager = new MenuManager();
		menuManager.setRemoveAllWhenShown(true);
		menuManager.addMenuListener(new IMenuListener() {
			@Override
			public void menuAboutToShow(IMenuManager manager) {
				fillContextMenu(manager);
			}
		});
		table.setMenu(menuManager.createContextMenu(table));
		getSite().registerContextMenu(menuManager, tableViewer);
		getSite().setSelectionProvider(tableViewer);
	}

	private void update4TradersData(final Security security) {
		try {
			FourTraders fourTraders = new FourTraders();
			String financialsUrl = security.getConfigurationValue("fourTrasdersUrl");
			if (StringUtils.isBlank(financialsUrl)) {
				financialsUrl = fourTraders.scrapFinancialsUrl(security.getISIN());
				security.putConfigurationValue("fourTrasdersUrl", financialsUrl);
			}
			if (StringUtils.isNotBlank(financialsUrl)) {
				updateFundamentaData(fourTraders.scrapFinancials(financialsUrl));
			} else {
				String errorText = "No unique result could be found for "+security.getISIN();
				IStatus status = new Status(IStatus.ERROR, Activator.PLUGIN_ID, errorText);
				ErrorDialog.openError(getSite().getShell(), errorText, null, status);
			}
		} catch (RuntimeException ex) {
			ex.printStackTrace();
		}
	}

	private void updateFundamentaData(List<FundamentalData> newDatas) {
		for (FundamentalData newData : newDatas) {
			if (newData.getDividende().signum() == 0 && newData.getEarningsPerShare().signum() == 0) {
				continue;
			}
			boolean dataExists = false;
			for (FundamentalData oldData : fundamentalDatas) {
				if (newData.getYear() == oldData.getYear()) {
					oldData.update(newData);
					dataExists = true;
					break;
				}
			}
			if (! dataExists) {
				fundamentalDatas.add(newData);
				tableRows.add(newData);
			}
		}
		markDirty();
		tableViewer.refresh(true);
	}

	private List<FundamentalData> cloneFundamentalData(Collection<FundamentalData> datas) {
		List<FundamentalData> fundamentalDatas = new ArrayList<FundamentalData>();
		for (FundamentalData d : datas) {
			fundamentalDatas.add(new FundamentalData(d));
		}
		Collections.sort(fundamentalDatas);
		return fundamentalDatas;
	}

	@Override
	public void setFocus() {
		tableViewer.getTable().setFocus();
	}

	protected void fillContextMenu(IMenuManager manager) {
		manager.add(new Action("New") {
			@Override
			public void run() {
				FundamentalData fundamentalData = new FundamentalData();
				fundamentalDatas.add(fundamentalData);
				tableRows.add(fundamentalData);
				tableViewer.add(fundamentalData);
				markDirty();
			}
		});
	}

	public void deleteFundamentalData(Collection<FundamentalData> data) {
		if (fundamentalDatas.removeAll(data)) {
			tableRows.removeAll(data);
			tableViewer.remove(data.toArray());
			markDirty();
		}
	}

	public void ignoreFundamentalData(Collection<FundamentalData> data) {
		for (FundamentalData fundamentalData : data) {
			fundamentalData.setIgnoreInAvgCalculation(! fundamentalData.isIgnoreInAvgCalculation());
			tableViewer.refresh();
			markDirty();
		}
	}

	public void lockFundamentalData(Collection<FundamentalData> data) {
		for (FundamentalData fundamentalData : data) {
			fundamentalData.setLocked(! fundamentalData.isLocked());
			tableViewer.refresh();
			markDirty();
		}
	}

	@Override
	public void doSave(IProgressMonitor monitor) {
		Security security = getSecurity();
		List<FundamentalData> datas = cloneFundamentalData(fundamentalDatas);
		Currency selectedCurrency = currencyComboViewer.getSelectedCurrency();
		for (FundamentalData fundamentalData : datas) {
			fundamentalData.setCurrency(selectedCurrency);
		}
		security.setFundamentalDatas(datas);
		dirty = false;
	}

	private Security getSecurity() {
		return ((SecurityEditorInput) getEditorInput()).getSecurity();
	}

	@Override
	public void doSaveAs() {
	}

	@Override
	public boolean isDirty() {
		return dirty;
	}

	@Override
	public boolean isSaveAsAllowed() {
		return false;
	}

	public void markDirty() {
		dirty = true;
		firePropertyChange(IEditorPart.PROP_DIRTY);
	}

}
