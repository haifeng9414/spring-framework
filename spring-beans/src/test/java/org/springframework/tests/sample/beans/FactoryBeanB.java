package org.springframework.tests.sample.beans;

import org.springframework.beans.factory.FactoryBean;

public class FactoryBeanB implements FactoryBean<BeanB> {
	private BeanA beanA;

	@Override
	public BeanB getObject() throws Exception {
		BeanB beanB = new BeanB("2", "beanB");
		beanB.setBeanA(beanA);
		return beanB;
	}

	@Override
	public Class<?> getObjectType() {
		return BeanB.class;
	}

	public BeanA getBeanA() {
		return beanA;
	}

	public void setBeanA(BeanA beanA) {
		this.beanA = beanA;
	}
}
