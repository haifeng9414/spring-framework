package org.springframework.tests.sample.beans;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class MyBeanAop {

	@Pointcut("execution(* *.test(..))")
	public void test() {
	}

	@Before("test()")
	public void getNameAdvice() {
		System.out.println("before");
	}

	@After("test()")
	public void getAllAdvice() {
		System.out.println("after");
	}
}
