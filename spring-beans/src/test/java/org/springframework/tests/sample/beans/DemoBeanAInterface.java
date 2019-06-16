package org.springframework.tests.sample.beans;

public interface DemoBeanAInterface {
	public String getId();

	public void setId(String id);

	public String getName();

	public void setName(String name);

	public DemoBeanBInterface getDemoBeanB();

	public void setDemoBeanB(DemoBeanBInterface demoBeanB);
}
