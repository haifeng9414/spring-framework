/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.aop.framework;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.aopalliance.intercept.Interceptor;
import org.aopalliance.intercept.MethodInterceptor;

import org.springframework.aop.Advisor;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.aop.support.MethodMatchers;
import org.springframework.lang.Nullable;

/**
 * A simple but definitive way of working out an advice chain for a Method,
 * given an {@link Advised} object. Always rebuilds each advice chain;
 * caching can be provided by subclasses.
 *
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @author Adrian Colyer
 * @since 2.0.3
 */
@SuppressWarnings("serial")
public class DefaultAdvisorChainFactory implements AdvisorChainFactory, Serializable {

	@Override
	public List<Object> getInterceptorsAndDynamicInterceptionAdvice(
			Advised config, Method method, @Nullable Class<?> targetClass) {

		// This is somewhat tricky... We have to process introductions first,
		// but we need to preserve order in the ultimate list.
		List<Object> interceptorList = new ArrayList<>(config.getAdvisors().length);
		Class<?> actualClass = (targetClass != null ? targetClass : method.getDeclaringClass());
		// 判断config中保存的advisor中是否有IntroductionAdvisor类型的，并且match传入的actualClass
		boolean hasIntroductions = hasMatchingIntroductions(config, actualClass);
		AdvisorAdapterRegistry registry = GlobalAdvisorAdapterRegistry.getInstance();

		// 这里获取到的config.getAdvisors()一般都是ReflectiveAspectJAdvisorFactory的getAdvisor方法返回的
		// InstantiationModelAwarePointcutAdvisorImpl对象，该对象是PointcutAdvisor类型的，默认根据aspect bean的aspectj表达式
		// 对类和方法进行过滤
		// 当然还有AbstractAdvisorAutoProxyCreator的extendAdvisors方法中默认加在首位的ExposeInvocationInterceptor，用于
		// 保存MethodInvocation到ThreadLocal
		for (Advisor advisor : config.getAdvisors()) {
			// PointcutAdvisor是通过Pointcut对advice的适用性进行过滤
			if (advisor instanceof PointcutAdvisor) {
				// Add it conditionally.
				PointcutAdvisor pointcutAdvisor = (PointcutAdvisor) advisor;
				// 如果已经过滤过就不用再过滤了
				if (config.isPreFiltered() || pointcutAdvisor.getPointcut().getClassFilter().matches(actualClass)) {
					// getInterceptors相当于根据传入的advisor获取Interceptor，如果advisor中的advice是MethodInterceptor类型的则直接返回，
					// 否则遍历registry中的所有AdvisorAdapter，如果某个AdvisorAdapter支持对advisor中的advice进行适配，则进行适配后返回
					MethodInterceptor[] interceptors = registry.getInterceptors(advisor);
					MethodMatcher mm = pointcutAdvisor.getPointcut().getMethodMatcher();
					// 判断当前方法是否符合当前遍历的advisor的pointcut
					if (MethodMatchers.matches(mm, method, actualClass, hasIntroductions)) {
						// 如果是runtime的，则表示方法参数对pointcut的匹配结果有影响，则在上面的if中，MethodMatchers.matches方法即使调用了
						// matches(Method method, @Nullable Class<?> targetClass)方法进行判断，在之后最终执行advice之前还要执行matches(Method method, @Nullable Class<?> targetClass, Object... args)
						// 方法再次判断，这里用InterceptorAndDynamicMethodMatcher组合MethodInterceptor和MethodMatcher，在ReflectiveMethodInvocation对象执行方法调用时再进行match判断
						if (mm.isRuntime()) {
							// Creating a new object instance in the getInterceptors() method
							// isn't a problem as we normally cache created chains.
							for (MethodInterceptor interceptor : interceptors) {
								interceptorList.add(new InterceptorAndDynamicMethodMatcher(interceptor, mm));
							}
						}
						else {
							// 如果不是runtime的则直接添加到interceptorList即可
							interceptorList.addAll(Arrays.asList(interceptors));
						}
					}
				}
			}
			// IntroductionAdvisor是通过ClassFilter对advice的适用性进行过滤
			else if (advisor instanceof IntroductionAdvisor) {
				IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
				// IntroductionAdvisor只能用classFilter进行过滤
				if (config.isPreFiltered() || ia.getClassFilter().matches(actualClass)) {
					Interceptor[] interceptors = registry.getInterceptors(advisor);
					interceptorList.addAll(Arrays.asList(interceptors));
				}
			}
			else {
				// 其他类型的advisor不做过滤
				Interceptor[] interceptors = registry.getInterceptors(advisor);
				interceptorList.addAll(Arrays.asList(interceptors));
			}
		}

		return interceptorList;
	}

	/**
	 * Determine whether the Advisors contain matching introductions.
	 */
	private static boolean hasMatchingIntroductions(Advised config, Class<?> actualClass) {
		for (int i = 0; i < config.getAdvisors().length; i++) {
			Advisor advisor = config.getAdvisors()[i];
			if (advisor instanceof IntroductionAdvisor) {
				IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
				if (ia.getClassFilter().matches(actualClass)) {
					return true;
				}
			}
		}
		return false;
	}

}
