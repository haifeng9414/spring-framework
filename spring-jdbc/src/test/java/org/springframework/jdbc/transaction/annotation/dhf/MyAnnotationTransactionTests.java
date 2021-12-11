package org.springframework.jdbc.transaction.annotation.dhf;

import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.transaction.annotation.dhf.book.dao.BookDao;
import org.springframework.jdbc.transaction.annotation.dhf.book.model.Book;

public class MyAnnotationTransactionTests {
	ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("org/springframework/transaction/MyAnnotationTransactionTests.xml");
	@Test
	public void baseTest() {
		BookDao bookDao = (BookDao) context.getBean("bookDao");
		System.out.println(bookDao.getAll());
	}

	@Test
	public void requiredTest() {
		BookDao bookDao = (BookDao) context.getBean("bookDao");
		int count = bookDao.getAll().size();
		System.out.println("count: " + count);

		try {
			System.out.println("insertWithException");
			bookDao.insertWithException(new Book() {{
				setName("test");
				setYear(1);
			}});
		} catch (Exception e) {
			System.out.println(e.getMessage());;
		}

		count = bookDao.getAll().size();
		System.out.println("count: " + count);

		try {
			System.out.println("insert");
			bookDao.insert(new Book() {{
				setName("test");
				setYear(1);
			}});
		} catch (Exception e) {
			e.printStackTrace();
		}

		count = bookDao.getAll().size();
		System.out.println("count: " + count);
	}
}
