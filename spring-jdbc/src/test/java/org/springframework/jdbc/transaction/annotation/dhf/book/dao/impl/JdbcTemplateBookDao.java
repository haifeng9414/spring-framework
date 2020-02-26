package org.springframework.jdbc.transaction.annotation.dhf.book.dao.impl;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.support.JdbcDaoSupport;
import org.springframework.jdbc.transaction.annotation.dhf.book.dao.BookDao;
import org.springframework.jdbc.transaction.annotation.dhf.book.model.Book;
import org.springframework.jdbc.transaction.annotation.dhf.book.model.BookRowMapper;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class JdbcTemplateBookDao extends JdbcDaoSupport implements BookDao {
    @Override
    public void insert(Book book) {
        String sql = "INSERT INTO BOOK(BOOK_ID, NAME, YEAR) VALUES (?, ?, ?)";

        getJdbcTemplate().update(sql, new Object[]{book.getBookId(), book.getName(), book.getYear()});
    }
    @Override
    public void insertWithException(Book book) {
        String sql = "INSERT INTO BOOK(BOOK_ID, NAME, YEAR) VALUES (?, ?, ?)";

        getJdbcTemplate().update(sql, new Object[]{book.getBookId(), book.getName(), book.getYear()});
        throw new RuntimeException("test insert exception");
    }

    @Override
    public void insertBatch(final List<Book> books) {
        String sql = "INSERT INTO BOOK (BOOK_ID, NAME, YEAR) VALUES (?, ?, ?)";

        getJdbcTemplate().batchUpdate(sql, new BatchPreparedStatementSetter() {

            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Book customer = books.get(i);
                ps.setLong(1, customer.getBookId());
                ps.setString(2, customer.getName());
                ps.setInt(3, customer.getYear());
            }

            public int getBatchSize() {
                return books.size();
            }
        });
    }

    @Override
    public void delete(Book book) {
        String sql = "DELETE FROM BOOK WHERE BOOK_ID=?";
        getJdbcTemplate().update(sql, new Object[]{book.getBookId()});
    }

    @Override
    public void deleteAll() {
        String sql = "DELETE FROM BOOK";
        getJdbcTemplate().update(sql);
    }

    @Override
    public Book getById(Integer id) {
        String sql = "SELECT * FROM BOOK WHERE BOOK_ID=?";
        return (Book) getJdbcTemplate().queryForObject(sql, new Object[]{id}, new BookRowMapper());
    }

    @Override
    public List<Book> getAll() {
        String sql = "SELECT * FROM BOOK";
        return getJdbcTemplate().query(sql, new BookRowMapper());
    }
}
