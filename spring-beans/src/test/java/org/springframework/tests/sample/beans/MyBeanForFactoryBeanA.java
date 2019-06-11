package org.springframework.tests.sample.beans;

public class MyBeanForFactoryBeanA implements MyBeanForFactoryBeanAInterface{
	private String id;
	private String name;
	private MyBeanForFactoryBeanB myBeanForFactoryBeanB;

	public MyBeanForFactoryBeanA(String id, String name, MyBeanForFactoryBeanB myBeanForFactoryBeanB) {
		this.id = id;
		this.name = name;
		this.myBeanForFactoryBeanB = myBeanForFactoryBeanB;
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

	public MyBeanForFactoryBeanB getMyBeanForFactoryBeanB() {
		return myBeanForFactoryBeanB;
	}

	public void setMyBeanForFactoryBeanB(MyBeanForFactoryBeanB myBeanForFactoryBeanB) {
		this.myBeanForFactoryBeanB = myBeanForFactoryBeanB;
	}
}
