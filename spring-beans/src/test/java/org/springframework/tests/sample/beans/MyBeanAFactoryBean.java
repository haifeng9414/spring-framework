package org.springframework.tests.sample.beans;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;

public class MyBeanAFactoryBean implements FactoryBean<MyBeanA>, BeanFactoryAware {
	private BeanFactory beanFactory;

	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public Class<?> getObjectType() {
		return MyBeanA.class;
	}

	@Override
	public MyBeanA getObject() throws Exception {
		MyBeanB bean = beanFactory.getBean(MyBeanB.class);
		return new MyBeanA("1", "2");
	}

	@Override
	public boolean isSingleton() {
		return true;
	}
}
