package org.springframework.tests.sample.beans;

import org.springframework.beans.factory.config.AbstractFactoryBean;

public class MyFactoryBeanForFactoryBeanA extends AbstractFactoryBean<MyBeanForFactoryBeanA> {
	private MyBeanForFactoryBeanB myBeanForFactoryBeanB;

	@Override
	protected MyBeanForFactoryBeanA createInstance() throws Exception {
		return new MyBeanForFactoryBeanA("1", "beanA", myBeanForFactoryBeanB);
	}

	@Override
	public Class<?> getObjectType() {
		return MyBeanForFactoryBeanAInterface.class;
	}

	public MyBeanForFactoryBeanB getMyBeanForFactoryBeanB() {
		return myBeanForFactoryBeanB;
	}

	public void setMyBeanForFactoryBeanB(MyBeanForFactoryBeanB myBeanForFactoryBeanB) {
		this.myBeanForFactoryBeanB = myBeanForFactoryBeanB;
	}
}
