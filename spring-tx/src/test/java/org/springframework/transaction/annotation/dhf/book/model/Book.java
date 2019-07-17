package org.springframework.transaction.annotation.dhf.book.model;

public class Book {
    private long bookId;
    private String name;
    private int year;

    public Book() {
    }

    public Book(long bookId, String name, int year) {
        this.bookId = bookId;
        this.name = name;
        this.year = year;
    }

    public long getBookId() {
        return bookId;
    }

    public void setBookId(long bookId) {
        this.bookId = bookId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    @Override
    public String toString() {
        return "Book{" +
                "bookId=" + bookId +
                ", name='" + name + '\'' +
                ", year=" + year +
                '}';
    }
}
