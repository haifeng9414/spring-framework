package org.springframework.tests.sample.beans;

public class MyBeanA {
	private String id;
	private String name;
	private String prop;
	private String age;
	private MyBeanB myBeanB;

	public MyBeanA(String id, String name) {
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

	public String getProp() {
		return prop;
	}

	public void setProp(String prop) {
		this.prop = prop;
	}

	public String getAge() {
		return age;
	}

	public void setAge(String age) {
		this.age = age;
	}

	public MyBeanB getMyBeanB() {
		return myBeanB;
	}

	public void setMyBeanB(MyBeanB myBeanB) {
		this.myBeanB = myBeanB;
	}
}
