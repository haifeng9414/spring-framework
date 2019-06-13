package org.springframework.tests.sample.beans;

import org.springframework.beans.factory.FactoryBean;

public class FactoryBeanA implements FactoryBean<BeanA> {
	private BeanB beanB;

	@Override
	public BeanA getObject() throws Exception {
		BeanA beanA = new BeanA("1", "beanA");
		beanA.setBeanB(beanB);
		return beanA;
	}

	@Override
	public Class<?> getObjectType() {
		return BeanA.class;
	}

	public BeanB getBeanB() {
		return beanB;
	}

	public void setBeanB(BeanB beanB) {
		this.beanB = beanB;
	}
}
