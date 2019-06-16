package org.springframework.tests.sample.beans;

public class DemoBeanB implements DemoBeanBInterface {
	private String id;
	private String name;
	private DemoBeanAInterface demoBeanA;

	public DemoBeanB(String id, String name) {
		System.out.println("new DemoBeanB");
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

	public DemoBeanAInterface getDemoBeanA() {
		return demoBeanA;
	}

	public void setDemoBeanA(DemoBeanAInterface demoBeanA) {
		this.demoBeanA = demoBeanA;
	}
}
