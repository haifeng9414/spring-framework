package org.springframework.jdbc.transaction.annotation.dhf.book.service;

import org.springframework.jdbc.transaction.annotation.dhf.book.dao.impl.User1DaoImpl;
import org.springframework.jdbc.transaction.annotation.dhf.book.model.User1;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class User1Service {
	private User1DaoImpl user1Dao;

	public User1DaoImpl getUser1Dao() {
		return user1Dao;
	}

	public void setUser1Dao(User1DaoImpl user1Dao) {
		this.user1Dao = user1Dao;
	}

	public int getLargestUserId() {
		return user1Dao.getLargestUserId();
	}

	@Transactional(propagation = Propagation.REQUIRED)
	public void addRequired(User1 user) {
		user1Dao.insert(user);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void addRequireNew(User1 user) {
		user1Dao.insert(user);
	}

	@Transactional(propagation = Propagation.NESTED)
	public void addNested(User1 user) {
		user1Dao.insert(user);
	}
}