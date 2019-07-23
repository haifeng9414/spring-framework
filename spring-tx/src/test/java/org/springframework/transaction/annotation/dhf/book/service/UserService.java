package org.springframework.transaction.annotation.dhf.book.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.dhf.book.model.User1;
import org.springframework.transaction.annotation.dhf.book.model.User2;

@Service
public class UserService {
	private User1Service user1Service;
	private User2Service user2Service;

	public User1Service getUser1Service() {
		return user1Service;
	}

	public void setUser1Service(User1Service user1Service) {
		this.user1Service = user1Service;
	}

	public User2Service getUser2Service() {
		return user2Service;
	}

	public void setUser2Service(User2Service user2Service) {
		this.user2Service = user2Service;
	}

	public void printLargestUserId() {
		System.out.println("user1: " + user1Service.getLargestUserId());
		System.out.println("user2: " + user2Service.getLargestUserId());
	}

	@Transactional(propagation = Propagation.REQUIRED)
	public void addRequired() {
		User1 user1 = new User1();
		user1.setName("张三");
		user1Service.addRequired(user1);

		User2 user2 = new User2();
		user2.setName("李四");
		user2Service.addRequired(user2);
	}

	@Transactional(propagation = Propagation.REQUIRED)
	public void addRequiredWithExceptionAndTry() {
		User1 user1 = new User1();
		user1.setName("张三");
		user1Service.addRequired(user1);

		User2 user2 = new User2();
		user2.setName("李四");
		try {
			user2Service.addRequiredWithException(user2);
		} catch (Exception e) {
			System.out.println(e.getClass().getName());
		}
	}

	@Transactional(propagation = Propagation.REQUIRED)
	public void addRequireNew() {
		User1 user1 = new User1();
		user1.setName("张三");
		user1Service.addRequireNew(user1);

		User2 user2 = new User2();
		user2.setName("李四");
		user2Service.addRequireNew(user2);
	}

	@Transactional(propagation = Propagation.REQUIRED)
	public void addRequireNewWithException() {
		User1 user1 = new User1();
		user1.setName("张三");
		user1Service.addRequireNew(user1);

		User2 user2 = new User2();
		user2.setName("李四");
		user2Service.addRequireNewWithException(user2);
	}

	@Transactional(propagation = Propagation.REQUIRED)
	public void addRequireNewWithExceptionAndTry() {
		User1 user1 = new User1();
		user1.setName("张三");
		user1Service.addRequireNew(user1);

		User2 user2 = new User2();
		user2.setName("李四");
		try {
			user2Service.addRequireNewWithException(user2);
		} catch (Exception e) {
			System.out.println(e.getClass().getName());
		}
	}

	@Transactional(propagation = Propagation.REQUIRED)
	public void addNestedWithExceptionAndTry() {
		User1 user1 = new User1();
		user1.setName("张三");
		user1Service.addNested(user1);

		User2 user2 = new User2();
		user2.setName("李四");
		try {
			user2Service.addNestedWithException(user2);
		} catch (Exception e) {
			System.out.println(e.getClass().getName());
		}
	}

	@Transactional(propagation = Propagation.REQUIRED)
	public void addNestedWithExternalException() {
		User1 user1 = new User1();
		user1.setName("张三");
		user1Service.addNested(user1);

		User2 user2 = new User2();
		user2.setName("李四");
		user2Service.addNested(user2);

		throw new RuntimeException();
	}
}
