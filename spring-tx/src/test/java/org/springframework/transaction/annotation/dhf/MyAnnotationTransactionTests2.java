package org.springframework.transaction.annotation.dhf;

import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.transaction.annotation.dhf.book.service.UserService;

public class MyAnnotationTransactionTests2 {
	private ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("org/springframework/transaction/annotation/dhf/MyAnnotationTransactionTests.xml");
	private UserService userService = context.getBean("userService", UserService.class);

	@Test
	public void baseTest() {
		userService.addRequired();
	}

	@Test
	public void printTest() {
		userService.printLargestUserId();
	}

	@Test
	public void requiredTest() {
		userService.printLargestUserId();
		userService.addRequired();
		userService.printLargestUserId();
	}

	@Test
	public void requiredWithExceptionAndTryTest() {
//		userService.printLargestUserId();
		userService.addRequiredWithExceptionAndTry();
//		userService.printLargestUserId();
	}

	@Test
	public void requireNewTest() {
//		userService.printLargestUserId();
		userService.addRequireNew();
//		userService.printLargestUserId();
	}

	@Test
	public void requireNewWithExceptionTest() {
		userService.printLargestUserId();
		userService.addRequireNewWithException();
		userService.printLargestUserId();
	}

	@Test
	public void requireNewWithExceptionAndTryTest() {
		userService.printLargestUserId();
		userService.addRequireNewWithExceptionAndTry();
		userService.printLargestUserId();
	}

	@Test
	public void nestedWithExceptionAndTryTest() {
		userService.printLargestUserId();
		userService.addNestedWithExceptionAndTry();
		userService.printLargestUserId();
	}

	@Test
	public void nestedWithExternalExceptionTest() {
		userService.printLargestUserId();
		try {
			userService.addNestedWithExternalException();
		} catch (Exception e) {
			System.out.println(e.getClass().getName());
		}
		userService.printLargestUserId();
	}
}
