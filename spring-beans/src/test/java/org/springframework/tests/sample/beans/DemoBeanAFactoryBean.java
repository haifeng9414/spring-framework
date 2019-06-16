package org.springframework.tests.sample.beans;

import org.springframework.beans.factory.config.AbstractFactoryBean;

public class DemoBeanAFactoryBean extends AbstractFactoryBean<DemoBeanAInterface> {
	private DemoBeanBInterface demoBeanBFactoryBean;

	public DemoBeanBInterface getDemoBeanBFactoryBean() {
		return demoBeanBFactoryBean;
	}

	public void setDemoBeanBFactoryBean(DemoBeanBInterface demoBeanBFactoryBean) {
		this.demoBeanBFactoryBean = demoBeanBFactoryBean;
	}

	@Override
	public Class<?> getObjectType() {
		return DemoBeanAInterface.class;
	}

	@Override
	protected DemoBeanAInterface createInstance() throws Exception {
		DemoBeanA demoBeanA = new DemoBeanA("1", "demoBeanA");
		demoBeanA.setDemoBeanB(demoBeanBFactoryBean);
		return demoBeanA;
	}
}
