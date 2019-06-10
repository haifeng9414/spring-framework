package org.springframework.tests.sample.beans;

import org.springframework.beans.factory.FactoryBean;

public class MyBeanFactoryBean implements FactoryBean<MyBean> {
	@Override
	public Class<?> getObjectType() {
		return MyBeanA.class;
	}

	@Override
	public MyBean getObject() throws Exception {
		return new MyBean("1", "2");
	}

	@Override
	public boolean isSingleton() {
		return true;
	}
}
