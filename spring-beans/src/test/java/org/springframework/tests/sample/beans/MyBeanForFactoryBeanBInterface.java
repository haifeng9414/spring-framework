package org.springframework.tests.sample.beans;

public interface MyBeanForFactoryBeanBInterface {
	public String getId();

	public void setId(String id);

	public String getName();

	public void setName(String name);

	public MyBeanForFactoryBeanAInterface getMyBeanForFactoryBeanA();

	public void setMyBeanForFactoryBeanA(MyBeanForFactoryBeanAInterface myBeanForFactoryBeanA);
}
