package de.tomsplayground.peanuts.client.widgets;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

import com.google.common.collect.ImmutableList;

import de.tomsplayground.peanuts.domain.base.ITransactionProvider;
import de.tomsplayground.peanuts.domain.process.ITransaction;
import de.tomsplayground.peanuts.domain.process.Transaction;
import de.tomsplayground.util.Day;

public class TransactionListContentProvider implements ITreeContentProvider {

	public static final class TimeTreeNode {
		private final Day date;
		private final ITransactionProvider provider;
		
		public TimeTreeNode(Day date, ITransactionProvider provider) {
			this.date = date;
			this.provider = provider;
		}
		public Day getDate() {
			return date;
		}
		public ImmutableList<ITransaction> getTransactions() {
			return provider.getTransactionsByDate(new Day(date.year, 0, 1), new Day(date.year, 11, 31));
		}
	}
	
	@Override
	public Object[] getElements(Object inputElement) {
		ITransactionProvider provider = (ITransactionProvider) inputElement;
		List<TransactionListContentProvider.TimeTreeNode> nodes = new ArrayList<TransactionListContentProvider.TimeTreeNode>();
		Day start, end;
		start = provider.getMinDate()!=null?provider.getMinDate():new Day();
		end = provider.getMaxDate()!=null?provider.getMaxDate():new Day();
		for (int year = start.year; year <= end.year; year++) {
			Day d = new Day(year, 0, 1);
			TimeTreeNode treeNode = new TransactionListContentProvider.TimeTreeNode(d, provider);
			nodes.add(treeNode);
		}
		return nodes.toArray();
	}

	@Override
	public Object[] getChildren(Object parentElement) {
		if (parentElement instanceof TimeTreeNode) {
			TimeTreeNode node = (TimeTreeNode) parentElement;
			return node.getTransactions().toArray();
		}
		Transaction t = (Transaction) parentElement;
		return t.getSplits().toArray();
	}

	@Override
	public boolean hasChildren(Object element) {
		if (element instanceof TimeTreeNode) {
			return ! ((TimeTreeNode) element).getTransactions().isEmpty();
		}
		ITransaction t = (ITransaction) element;
		return !t.getSplits().isEmpty();
	}

	@Override
	public Object getParent(Object element) {
		return null;
	}

	@Override
	public void dispose() {
		// nothing to do
	}

	@Override
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		// nothing to do
	}
}
