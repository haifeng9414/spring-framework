package org.springframework.tests.sample.beans.property;

public class MyBeanB {
	private String name;
	private MyBeanA myBeanA;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public MyBeanA getMyBeanA() {
		return myBeanA;
	}

	public void setMyBeanA(MyBeanA myBeanA) {
		this.myBeanA = myBeanA;
	}
}
