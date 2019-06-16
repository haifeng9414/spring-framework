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

package org.springframework.beans.factory.xml.property;

import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.tests.sample.beans.property.TestPropertyPopulateBean;

import java.util.Arrays;

public class XmlBeanFactoryPropertyTests {

	private static final Class<?> CLASS = XmlBeanFactoryPropertyTests.class;
	private static final String CLASSNAME = CLASS.getSimpleName();

	private static ClassPathResource classPathResource(String suffix) {
		return new ClassPathResource(CLASSNAME + suffix, CLASS);
	}

	@Test
	public void testPropertyPopulate() {
		ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext(classPathResource("-application-context.xml").getPath(), getClass());
		TestPropertyPopulateBean testPropertyPopulateBean = applicationContext.getBean("testPropertyPopulateBean", TestPropertyPopulateBean.class);
		System.out.println(testPropertyPopulateBean.getName());
		System.out.println(testPropertyPopulateBean.getNum());
		System.out.println(testPropertyPopulateBean.getTime());
		System.out.println(testPropertyPopulateBean.getMyBeanA().getName());
		System.out.println(testPropertyPopulateBean.getMyBeanB().getName());
		System.out.println(testPropertyPopulateBean.getStringList());
		System.out.println(Arrays.toString(testPropertyPopulateBean.getStringArray()));
		System.out.println(testPropertyPopulateBean.getStringMap());
	}
}
