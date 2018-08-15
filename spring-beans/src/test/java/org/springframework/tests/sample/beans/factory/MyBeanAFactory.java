package org.springframework.tests.sample.beans.factory;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.tests.sample.beans.MyBeanA;

public class MyBeanAFactory implements FactoryBean<MyBeanA> {
	@Override
	public MyBeanA getObject() throws Exception {
		return new MyBeanA("1", "2");
	}

	@Override
	public Class<?> getObjectType() {
		return MyBeanA.class;
	}
}
