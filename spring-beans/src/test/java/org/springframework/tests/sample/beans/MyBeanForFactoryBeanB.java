package org.springframework.tests.sample.beans;

public class MyBeanForFactoryBeanB implements MyBeanForFactoryBeanBInterface {
	private String id;
	private String name;
	private MyBeanForFactoryBeanA myBeanForFactoryBeanA;

	public MyBeanForFactoryBeanB(String id, String name, MyBeanForFactoryBeanA myBeanForFactoryBeanA) {
		this.id = id;
		this.name = name;
		this.myBeanForFactoryBeanA = myBeanForFactoryBeanA;
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

	public MyBeanForFactoryBeanA getMyBeanForFactoryBeanA() {
		return myBeanForFactoryBeanA;
	}

	public void setMyBeanForFactoryBeanA(MyBeanForFactoryBeanA myBeanForFactoryBeanA) {
		this.myBeanForFactoryBeanA = myBeanForFactoryBeanA;
	}
}
