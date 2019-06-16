package org.springframework.tests.sample.beans;

import org.springframework.beans.factory.config.AbstractFactoryBean;

public class DemoBeanBFactoryBean extends AbstractFactoryBean<DemoBeanBInterface> {
	private DemoBeanAInterface demoBeanAFactoryBean;

	public DemoBeanAInterface getDemoBeanAFactoryBean() {
		return demoBeanAFactoryBean;
	}

	public void setDemoBeanAFactoryBean(DemoBeanAInterface demoBeanAFactoryBean) {
		this.demoBeanAFactoryBean = demoBeanAFactoryBean;
	}

	@Override
	public Class<?> getObjectType() {
		return DemoBeanAInterface.class;
	}

	@Override
	protected DemoBeanBInterface createInstance() throws Exception {
		DemoBeanB demoBeanB = new DemoBeanB("2", "demoBeanB");
		demoBeanB.setDemoBeanA(demoBeanAFactoryBean);
		return demoBeanB;
	}
}
