package de.tomsplayground.peanuts.persistence;

import static org.junit.Assert.assertEquals;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;

import org.junit.Before;
import org.junit.Test;

import de.tomsplayground.peanuts.domain.base.Account;
import de.tomsplayground.peanuts.domain.base.AccountManager;
import de.tomsplayground.peanuts.domain.process.Transaction;
import de.tomsplayground.peanuts.domain.process.Transfer;
import de.tomsplayground.peanuts.persistence.xstream.PersistenceService;
import de.tomsplayground.util.Day;


public class PersistenceTest {

	private AccountManager accountManager;
	private Account account, account2;

	@Before
	public void setup() {
		accountManager = new AccountManager();
		account = accountManager.getOrCreateAccount("Test", Account.Type.BANK);
		account.addTransaction(new Transaction(new Day(), new BigDecimal("12.34")));
		account2 = accountManager.getOrCreateAccount("Test2", Account.Type.BANK);
		Transfer transfer = new Transfer(account, account2, new BigDecimal("1234.55"),
			new Day());
		account.addTransaction(transfer.getTransferFrom());
		account2.addTransaction(transfer.getTransferTo());
	}

	@Test
	public void testAccountManager() throws Exception {

		IPersistenceService persistence = new PersistenceService();
		String xml = persistence.write(accountManager);
		AccountManager accountManager2 = persistence.readAccountManager(xml);

		System.out.println(xml);

		assertEquals(accountManager.getAccounts().size(), accountManager2.getAccounts().size());
		Account accountToCheck = accountManager2.getAccounts().get(0);
		assertEquals(account.getName(), accountToCheck.getName());
		assertEquals(account.getBalance(), accountToCheck.getBalance());
		assertEquals(account.getTransactions().size(), accountToCheck.getTransactions().size());
	}

	@Test
	public void testPersistence() throws Exception {
		IPersistenceService persistenceService = new PersistenceService();
		Persistence persistence = new Persistence();
		persistence.setPersistenceService(persistenceService);

		StringWriter stringWriter = new StringWriter();
		persistence.write(stringWriter, accountManager);
		System.out.println("XML:" + stringWriter.toString());
		AccountManager accountManager2 = persistence.read(new StringReader(stringWriter.toString()));

		assertEquals(accountManager.getAccounts().size(), accountManager2.getAccounts().size());
		Account accountToCheck = accountManager2.getAccounts().get(0);
		assertEquals(account.getName(), accountToCheck.getName());
		assertEquals(account.getBalance(), accountToCheck.getBalance());
		assertEquals(account.getTransactions().size(), accountToCheck.getTransactions().size());
	}

	@Test
	public void testPersistenceFromFile() {
		Reader in = new InputStreamReader(PersistenceTest.class.getResourceAsStream("/persistence.xml"));
		IPersistenceService persistenceService = new PersistenceService();
		Persistence persistence = new Persistence();
		persistence.setPersistenceService(persistenceService);
		
		AccountManager accountManager2 = persistence.read(in);
		
		assertEquals(2, accountManager2.getAccounts().size());
		
	}
	
}
