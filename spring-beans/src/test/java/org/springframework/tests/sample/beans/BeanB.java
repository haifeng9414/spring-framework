package org.springframework.tests.sample.beans;

public class BeanB {
	private String id;
	private String name;
	private BeanA beanA;

	public BeanB(String id, String name) {
		System.out.println("new BeanB");
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

	public BeanA getBeanA() {
		return beanA;
	}

	public void setBeanA(BeanA beanA) {
		this.beanA = beanA;
	}
}
