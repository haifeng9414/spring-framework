package org.springframework.tests.sample.beans;

import java.util.Date;

public class MyBeanB {
	private String id;
	private String name;
	private Date date;
	private MyBeanA myBeanA;

	public MyBeanB(String id, String name) {
		this.id = id;
		this.name = name;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public MyBeanA getMyBeanA() {
		return myBeanA;
	}

	public void setMyBeanA(MyBeanA myBeanA) {
		this.myBeanA = myBeanA;
	}
}
