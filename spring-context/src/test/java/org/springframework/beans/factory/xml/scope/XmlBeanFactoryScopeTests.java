/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans.factory.xml.scope;

import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.tests.sample.beans.MyBeanA;

public class XmlBeanFactoryScopeTests {

	private static final Class<?> CLASS = XmlBeanFactoryScopeTests.class;
	private static final String CLASSNAME = CLASS.getSimpleName();

	private static ClassPathResource classPathResource(String suffix) {
		return new ClassPathResource(CLASSNAME + suffix, CLASS);
	}

	@Test
	public void testThreadScope() {
		ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext(classPathResource("-application-context.xml").getPath(), getClass());
		applicationContext.getBeanFactory().registerScope("thread", new SimpleThreadScope());
		MyBeanA myBeanA1 = applicationContext.getBean("myBeanA", MyBeanA.class);
		MyBeanA myBeanA2 = applicationContext.getBean("myBeanA", MyBeanA.class);
		System.out.println("myBeanA1 == myBeanA2:" + (myBeanA1 == myBeanA2));

		new Thread(() -> {
			MyBeanA myBeanA3 = applicationContext.getBean("myBeanA", MyBeanA.class);
			MyBeanA myBeanA4 = applicationContext.getBean("myBeanA", MyBeanA.class);
			System.out.println("myBeanA3 == myBeanA4:" + (myBeanA3 == myBeanA4));
			System.out.println("myBeanA1 == myBeanA3:" + (myBeanA1 == myBeanA3));
		}).start();
	}
}
