package org.springframework.transaction.annotation.dhf.book.model;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class BookRowMapper implements RowMapper {
    public Book mapRow(ResultSet rs, int rowNum) throws SQLException {
        Book book = new Book();
        book.setBookId(rs.getInt("BOOK_ID"));
        book.setName(rs.getString("NAME"));
        book.setYear(rs.getInt("YEAR"));
        return book;
    }
}
