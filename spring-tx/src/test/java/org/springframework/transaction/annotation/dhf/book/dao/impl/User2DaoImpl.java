package org.springframework.transaction.annotation.dhf.book.dao.impl;

import org.springframework.jdbc.core.support.JdbcDaoSupport;
import org.springframework.transaction.annotation.dhf.book.dao.User2Dao;
import org.springframework.transaction.annotation.dhf.book.model.User2;

public class User2DaoImpl extends JdbcDaoSupport implements User2Dao {
	@Override
	public int insert(User2 record) {
		String sql = "insert into user2(name) values (?)";

		return getJdbcTemplate().update(sql, new Object[]{record.getName()});
	}

	@Override
	public int getLargestUserId() {
		String sql = "select id from user2 order by id desc";

		return getJdbcTemplate().queryForList(sql, Integer.class).get(0);
	}
}
