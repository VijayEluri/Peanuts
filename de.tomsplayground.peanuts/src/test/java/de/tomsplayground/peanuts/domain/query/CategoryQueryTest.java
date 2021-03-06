package de.tomsplayground.peanuts.domain.query;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Collections2;

import de.tomsplayground.peanuts.domain.base.Category;
import de.tomsplayground.peanuts.domain.process.ITransaction;
import de.tomsplayground.peanuts.domain.process.Transaction;
import de.tomsplayground.util.Day;

public class CategoryQueryTest {

	private Category c1;
	private Category c2;
	private Transaction t1;
	private Transaction t11;
	private Transaction t2;
	private List<ITransaction> trans;

	@Before
	public void setUp() {
		c1 = new Category("c1", Category.Type.EXPENSE);
		Category c11 = new Category("c11", Category.Type.EXPENSE);
		c1.addChildCategory(c11);
		c2 = new Category("c2", Category.Type.EXPENSE);
		t1 = new Transaction(new Day(), BigDecimal.TEN, c1, "");
		t11 = new Transaction(new Day(), BigDecimal.TEN, c11, "");
		t2 = new Transaction(new Day(), BigDecimal.TEN, c2, "");
		Transaction tx = new Transaction(new Day(), BigDecimal.TEN);
		trans = new ArrayList<ITransaction>();
		trans.add(t1);
		trans.add(t11);
		trans.add(t2);
		trans.add(tx);
	}

	@Test
	public void testSimple() throws Exception {
		IQuery categoryQuery = new CategoryQuery(c2);
		Collection<ITransaction> result = Collections2.filter(trans, categoryQuery.getPredicate());

		assertEquals(1, result.size());
		assertEquals(t2, result.iterator().next());
	}

	@Test
	public void testParentCategory() throws Exception {
		IQuery categoryQuery = new CategoryQuery(c1);
		Collection<ITransaction> result = Collections2.filter(trans, categoryQuery.getPredicate());

		assertEquals(2, result.size());
		assertTrue(result.contains(t1));
		assertTrue(result.contains(t11));
	}

	@Test
	public void testList() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(c1);
		categories.add(c2);
		IQuery categoryQuery = new CategoryQuery(categories);
		Collection<ITransaction> result = Collections2.filter(trans, categoryQuery.getPredicate());

		assertEquals(3, result.size());
	}

	@Test
	public void testNonIdenticalCategory() throws Exception {
		Category c1NonIdentical = new Category("c1", Category.Type.EXPENSE);
		IQuery categoryQuery = new CategoryQuery(c1NonIdentical);
		Collection<ITransaction> result = Collections2.filter(trans, categoryQuery.getPredicate());

		assertEquals(2, result.size());
	}

}
