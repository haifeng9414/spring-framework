package org.springframework.tests.sample.beans;

import org.springframework.beans.factory.config.AbstractFactoryBean;

public class MyFactoryBeanForFactoryBeanB extends AbstractFactoryBean<MyBeanForFactoryBeanB> {
	private MyBeanForFactoryBeanA myBeanForFactoryBeanA;

	@Override
	protected MyBeanForFactoryBeanB createInstance() throws Exception {
		return new MyBeanForFactoryBeanB("2", "beanB", myBeanForFactoryBeanA);
	}

	@Override
	public Class<?> getObjectType() {
		return MyBeanForFactoryBeanBInterface.class;
	}

	public MyBeanForFactoryBeanA getMyBeanForFactoryBeanA() {
		return myBeanForFactoryBeanA;
	}

	public void setMyBeanForFactoryBeanA(MyBeanForFactoryBeanA myBeanForFactoryBeanA) {
		this.myBeanForFactoryBeanA = myBeanForFactoryBeanA;
	}
}
