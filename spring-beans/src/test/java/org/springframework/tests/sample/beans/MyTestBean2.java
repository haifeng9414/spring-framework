package org.springframework.tests.sample.beans;

public class MyTestBean2 {
	private MyTestBean myTestBean;

	public MyTestBean getMyTestBean() {
		return myTestBean;
	}

	public void setMyTestBean(MyTestBean myTestBean) {
		this.myTestBean = myTestBean;
	}

	public void test() {
		System.out.println("test...");
	}
}
