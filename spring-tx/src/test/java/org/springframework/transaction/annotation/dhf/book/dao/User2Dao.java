package org.springframework.transaction.annotation.dhf.book.dao;

import org.springframework.transaction.annotation.dhf.book.model.User2;

public interface User2Dao {
    int insert(User2 record);

	int getLargestUserId();
}