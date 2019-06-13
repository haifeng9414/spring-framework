package org.springframework.tests.sample.beans;

public class BeanA {
	private String id;
	private String name;
	private BeanB beanB;

	public BeanA(String id, String name) {
		System.out.println("new BeanA");
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

	public BeanB getBeanB() {
		return beanB;
	}

	public void setBeanB(BeanB beanB) {
		this.beanB = beanB;
	}
}
