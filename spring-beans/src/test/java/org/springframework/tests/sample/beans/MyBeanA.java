package org.springframework.tests.sample.beans;

import org.springframework.context.ApplicationListener;
import org.springframework.context.Lifecycle;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.concurrent.TimeUnit;

public class MyBeanA implements ApplicationListener<ContextRefreshedEvent>, Lifecycle {
	private String id;
	private String name;
	private String prop;
	private String age;
	private MyBeanB myBeanB;

	public MyBeanA(String id, String name) {
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

	public String getProp() {
		return prop;
	}

	public void setProp(String prop) {
		this.prop = prop;
	}

	public String getAge() {
		return age;
	}

	public void setAge(String age) {
		this.age = age;
	}

	public MyBeanB getMyBeanB() {
		return myBeanB;
	}

	public void setMyBeanB(MyBeanB myBeanB) {
		this.myBeanB = myBeanB;
	}

	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		System.out.println("mybean A onApplicationEvent");
	}

	@Override
	public void start() {
		System.out.println("myBeanA start");
	}

	@Override
	public void stop() {
		try {
			for (int i = 0; i < 20; i++) {
				System.out.println("myBeanA " + i);
				TimeUnit.SECONDS.sleep(1);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("myBeanA stop");
	}

	@Override
	public boolean isRunning() {
		return true;
	}
}
