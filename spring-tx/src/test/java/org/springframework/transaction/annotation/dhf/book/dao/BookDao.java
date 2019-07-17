package org.springframework.transaction.annotation.dhf.book.dao;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.dhf.book.model.Book;

import java.util.List;

@Transactional(propagation = Propagation.REQUIRED)
public interface BookDao {
    void insert(Book book);

    void insertWithException(Book book);

    void insertBatch(List<Book> books);

    void delete(Book book);

    void deleteAll();

    Book getById(Integer id);

    List<Book> getAll();
}
