package org.springframework.tests.sample.beans;

public class DemoBeanA implements DemoBeanAInterface {
	private String id;
	private String name;
	private DemoBeanBInterface demoBeanB;

	public DemoBeanA(String id, String name) {
		System.out.println("new DemoBeanA");
		this.id = id;
		this.name = name;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public DemoBeanBInterface getDemoBeanB() {
		return demoBeanB;
	}

	@Override
	public void setDemoBeanB(DemoBeanBInterface demoBeanB) {
		this.demoBeanB = demoBeanB;
	}
}
