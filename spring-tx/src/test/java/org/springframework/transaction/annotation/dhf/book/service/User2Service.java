package org.springframework.transaction.annotation.dhf.book.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.dhf.book.dao.impl.User2DaoImpl;
import org.springframework.transaction.annotation.dhf.book.model.User1;
import org.springframework.transaction.annotation.dhf.book.model.User2;

import javax.annotation.Resource;

@Service
public class User2Service {
	private User2DaoImpl user2Dao;

	public User2DaoImpl getUser2Dao() {
		return user2Dao;
	}

	public void setUser2Dao(User2DaoImpl user2Dao) {
		this.user2Dao = user2Dao;
	}

	public int getLargestUserId() {
		return user2Dao.getLargestUserId();
	}

	@Transactional(propagation = Propagation.REQUIRED)
    public void addRequired(User2 user){
		user2Dao.insert(user);
    }

	@Transactional(propagation = Propagation.REQUIRED)
    public void addRequiredWithException(User2 user){
		user2Dao.insert(user);
		throw new RuntimeException();
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void addRequireNew(User2 user) {
		user2Dao.insert(user);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void addRequireNewWithException(User2 user) {
		user2Dao.insert(user);
		throw new RuntimeException();
	}

	@Transactional(propagation = Propagation.NESTED)
	public void addNestedWithException(User2 user) {
		user2Dao.insert(user);
		throw new RuntimeException();
	}
}