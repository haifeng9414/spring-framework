package org.springframework.jdbc.transaction.annotation.dhf.book.dao.impl;

import org.springframework.jdbc.core.support.JdbcDaoSupport;
import org.springframework.jdbc.transaction.annotation.dhf.book.dao.User1Dao;
import org.springframework.jdbc.transaction.annotation.dhf.book.model.User1;

public class User1DaoImpl extends JdbcDaoSupport implements User1Dao {
	@Override
	public int insert(User1 record) {
		String sql = "INSERT INTO t_user(username) VALUES (?)";

		return getJdbcTemplate().update(sql, new Object[]{record.getName()});
	}

	@Override
	public int getLargestUserId() {
		String sql = "select id from t_user order by id desc";

		return getJdbcTemplate().queryForList(sql, Integer.class).get(0);
	}
}
