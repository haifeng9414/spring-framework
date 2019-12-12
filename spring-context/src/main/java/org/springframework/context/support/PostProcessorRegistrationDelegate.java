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

package org.springframework.context.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.lang.Nullable;

/**
 * Delegate for AbstractApplicationContext's post-processor handling.
 *
 * @author Juergen Hoeller
 * @since 4.0
 */
class PostProcessorRegistrationDelegate {

	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

		// Invoke BeanDefinitionRegistryPostProcessors first, if any.
		Set<String> processedBeans = new HashSet<>();

		if (beanFactory instanceof BeanDefinitionRegistry) {
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
			// 用于保存下面循环中不是BeanDefinitionRegistryPostProcessor类型的BeanFactoryPostProcessor
			List<BeanFactoryPostProcessor> regularPostProcessors = new LinkedList<>();
			// 用于保存下面循环中是BeanDefinitionRegistryPostProcessor类型的BeanFactoryPostProcessor
			List<BeanDefinitionRegistryPostProcessor> registryProcessors = new LinkedList<>();

			// BeanDefinitionRegistryPostProcessor是BeanFactoryPostProcessor接口的子类，只有一个方法postProcessBeanDefinitionRegistry，参数为
			// BeanDefinitionRegistry，用于在所有BeanDefinition注册完之后，bean被实例化之前修改或新增保存在BeanDefinitionRegistry里的BeanDefinition，
			// 这里获取类型是BeanDefinitionRegistryPostProcessor的BeanFactoryPostProcessor，调用postProcessBeanDefinitionRegistry方法
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
					BeanDefinitionRegistryPostProcessor registryProcessor =
							(BeanDefinitionRegistryPostProcessor) postProcessor;
					registryProcessor.postProcessBeanDefinitionRegistry(registry);
					registryProcessors.add(registryProcessor);
				}
				else {
					regularPostProcessors.add(postProcessor);
				}
			}

			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the bean factory post-processors apply to them!
			// Separate between BeanDefinitionRegistryPostProcessors that implement
			// PriorityOrdered, Ordered, and the rest.
			List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

			// First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
			// 除了上面保存在BeanFactory中保存的BeanFactoryPostProcessor中可能有BeanDefinitionRegistryPostProcessor，BeanFactory的Bean中也可能存在实现了
			// BeanDefinitionRegistryPostProcessor接口的bean，下面对这些bean做处理，注意此时这些bean还没有被实例化
			// 下面查找所有BeanDefinitionRegistryPostProcessor类型的bean，并分别调用实现了PriorityOrdered、Order和没有实现优先级相关接口的bean的postProcessBeanDefinitionRegistry监听方法
			String[] postProcessorNames =
					beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
					// 如果bean是实现了PriorityOrdered接口则直接实例化待后面调用postProcessBeanDefinitionRegistry方法
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					// 将获取到的beanName保存下来，以免之后再次循环时重复添加
					processedBeans.add(ppName);
				}
			}
			// 按照PriorityOrdered对currentRegistryProcessors进行排序
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			registryProcessors.addAll(currentRegistryProcessors);
			// 调用这些bean的postProcessBeanDefinitionRegistry方法
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			currentRegistryProcessors.clear();

			// Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
			// 和上面一样，这次获取实现了Ordered接口的BeanDefinitionRegistryPostProcessor类型的bean，这说明了PriorityOrdered优先级高于Ordered
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
				}
			}
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			registryProcessors.addAll(currentRegistryProcessors);
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			currentRegistryProcessors.clear();

			// Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
			boolean reiterate = true;
			while (reiterate) {
				reiterate = false;
				// 再次获取所有实现了BeanDefinitionRegistryPostProcessor接口的bean
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				for (String ppName : postProcessorNames) {
					// 如果beanName在上面的循环中还没有处理过，相当于获取所有实现了BeanDefinitionRegistryPostProcessor接口的bean中没有实现
					// PriorityOrdered接口和Ordered接口的bean
					if (!processedBeans.contains(ppName)) {
						currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
						processedBeans.add(ppName);
						// 由于BeanDefinitionRegistryPostProcessor的postProcessBeanDefinitionRegistry方法可能会添加新的BeanDefinition到BeanFactory
						// 而添加的BeanDefinition对应的bean可能实现了BeanDefinitionRegistryPostProcessor接口，所以这里用reiterate变量标记是否需要重新循环获取
						// 新添加的BeanDefinitionRegistryPostProcessor类型的bean，循环的开始将该变量设置成false，如果每次循环没有找过新的bean则不需要再循环了
						reiterate = true;
					}
				}
				// BeanDefinitionRegistryPostProcessor的postProcessBeanDefinitionRegistry方法添加的新的BeanDefinitionRegistryPostProcessor
				// 类型的bean可能实现了PriorityOrdered和Ordered接口，所以还是需要排序
				sortPostProcessors(currentRegistryProcessors, beanFactory);
				registryProcessors.addAll(currentRegistryProcessors);
				invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
				currentRegistryProcessors.clear();
			}

			// Now, invoke the postProcessBeanFactory callback of all processors handled so far.
			// BeanFactoryPostProcessor接口只有一个方法，postProcessBeanFactory，在BeanFactory被创建后调用，BeanDefinitionRegistryPostProcessor接口
			// 是BeanFactoryPostProcessor接口的子接口，下面调用所有BeanDefinitionRegistryPostProcessor和BeanFactoryPostProcessor的postProcessBeanFactory监听方法
			invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
		}

		else {
			// Invoke factory processors registered with the context instance.
			// 如果beanFactory不是BeanDefinitionRegistry类型的，则没必要寻找BeanDefinitionRegistryPostProcessor类型的bean了，这里
			// 直接调用传入的BeanFactoryPostProcessor的postProcessBeanFactory方法即可
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let the bean factory post-processors apply to them!
		// 上面的处理逻辑中，如果beanFactory不是BeanDefinitionRegistry则直接以传入的beanFactoryPostProcessors为参数调用postProcessBeanFactory方法，
		// 而传入的beanFactoryPostProcessors是保存在ApplicationContext中的成员变量而不是BeanFactory中bean，如果beanFactory是BeanDefinitionRegistry，也只是
		// 获取了所有BeanDefinitionRegistryPostProcessor类型的bean，这只是beanFactory中实现了BeanFactoryPostProcessor接口的bean的一部分，
		// 这样保存在beanFactory中的实现了BeanFactoryPostProcessor接口但没有实现BeanDefinitionRegistryPostProcessor的bean还没有被处理，所以下面处理这些bean，
		// 处理顺序和上面一样先处理实现了PriorityOrdered的bean，之后是实现了Order的，最后是剩下的，处理过程中跳过上面已经处理过得bean
		String[] postProcessorNames =
				beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		// Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		List<String> orderedPostProcessorNames = new ArrayList<>();
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		for (String ppName : postProcessorNames) {
			if (processedBeans.contains(ppName)) {
				// skip - already processed in first phase above
			}
			else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		// Next, invoke the BeanFactoryPostProcessors that implement Ordered.
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>();
		for (String postProcessorName : orderedPostProcessorNames) {
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		// Finally, invoke all other BeanFactoryPostProcessors.
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values...
		beanFactory.clearMetadataCache();
	}

	public static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {

		// 获取所有实现了BeanPostProcessor接口的bean
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

		// Register BeanPostProcessorChecker that logs an info message when
		// a bean is created during BeanPostProcessor instantiation, i.e. when
		// a bean is not eligible for getting processed by all BeanPostProcessors.
		// prepareBeanFactory添加了两个BeanPostProcessor到BeanFactory，这里用beanFactory.getBeanPostProcessorCount()获取已经添加到
		// BeanFactory的BeanPostProcessor，加上获取到的实现了BeanPostProcessor接口的bean的数量，再加上1以为下面一行会添加一个BeanPostProcessor，
		// 得到的结果表示期望的将被初始化的bean数量，也即是当前BeanFactory中BeanPostProcessor的数量
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
		// BeanPostProcessorChecker用于记录创建BeanPostProcessor过程中被创建的依赖bean，这些bean的创建是不会被BeanPostProcessor感知的，BeanPostProcessorChecker
		// 将这些bean记到日志中
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

		// Separate between BeanPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
		List<String> orderedPostProcessorNames = new ArrayList<>();
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		for (String ppName : postProcessorNames) {
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
				priorityOrderedPostProcessors.add(pp);
				// 创建Bean的过程中会合并BeanDefinition的属性（spring的xml标签支持在<bean>中定义bean，即内嵌bean，这种bean的BeanDefinition在用于创建bean时会先和父bean的BeanDefinition
				// 合并），而MergedBeanDefinitionPostProcessor接口的postProcessMergedBeanDefinition方法接收合并后的BeanDefinition、beanType和beanName，在创建bean时合并BeanDefinition
				// 后被调用
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					internalPostProcessors.add(pp);
				}
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, register the BeanPostProcessors that implement PriorityOrdered.
		// 按PriorityOrdered进行排序
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		// 添加BeanPostProcessor到BeanFactory
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

		// Next, register the BeanPostProcessors that implement Ordered.
		// 和上面一样的处理逻辑，处理实现了Ordered接口的bean
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>();
		for (String ppName : orderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			orderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		// Now, register all regular BeanPostProcessors.
		// 处理即没有实现PriorityOrdered接口也没有实现Ordered接口的bean
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
		for (String ppName : nonOrderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			nonOrderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

		// Finally, re-register all internal BeanPostProcessors.
		// internalPostProcessors保存了上面实现了MergedBeanDefinitionPostProcessor接口的BeanPostProcessor，这里进行排序
		sortPostProcessors(internalPostProcessors, beanFactory);
		// 排序后按照顺序重新注册，internalPostProcessors在上面处理的三个for循环中已经被注册到BeanFactory了，但是BeanFactory在注册BeanPostProcessor时会
		// 先移除BeanPostProcessor（如果有的话）再注册，所以不会存在重复注册的问题，而这里重新注册带来的效果是，如果BeanPostProcessor的实现类没有实现MergedBeanDefinitionPostProcessor接口，
		// 则这些BeanPostProcessor比是MergedBeanDefinitionPostProcessor类型的BeanPostProcessor优先级会更高，即使MergedBeanDefinitionPostProcessor类型的BeanPostProcessors实现了
		// PriorityOrdered接口，因为不是MergedBeanDefinitionPostProcessor类型的BeanPostProcessor不会被重新注册，而重新注册时BeanFactory会先移除再注册，这样
		// 使得重新注册的BeanPostProcessor在BeanFactory中的beanPostProcessors列表中更靠后，优先级就更低
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		// Re-register post-processor for detecting inner beans as ApplicationListeners,
		// moving it to the end of the processor chain (for picking up proxies etc).
		// ApplicationListenerDetector在prepareBeanFactory方法中已经注册过了，这里重新注册，使得ApplicationListenerDetectord在BeanPostProcessor中优先级最低
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
	}

	private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
		Comparator<Object> comparatorToUse = null;
		if (beanFactory instanceof DefaultListableBeanFactory) {
			comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
		}
		if (comparatorToUse == null) {
			comparatorToUse = OrderComparator.INSTANCE;
		}
		postProcessors.sort(comparatorToUse);
	}

	/**
	 * Invoke the given BeanDefinitionRegistryPostProcessor beans.
	 */
	private static void invokeBeanDefinitionRegistryPostProcessors(
			Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry) {

		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanDefinitionRegistry(registry);
		}
	}

	/**
	 * Invoke the given BeanFactoryPostProcessor beans.
	 */
	private static void invokeBeanFacto ryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanFactory(beanFactory);
		}
	}

	/**
	 * Register the given BeanPostProcessor beans.
	 */
	private static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

		for (BeanPostProcessor postProcessor : postProcessors) {
			beanFactory.addBeanPostProcessor(postProcessor);
		}
	}


	/**
	 * BeanPostProcessor that logs an info message when a bean is created during
	 * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
	 * getting processed by all BeanPostProcessors.
	 */
	private static class BeanPostProcessorChecker implements BeanPostProcessor {

		private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

		private final ConfigurableListableBeanFactory beanFactory;

		private final int beanPostProcessorTargetCount;

		public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
			this.beanFactory = beanFactory;
			this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			// beanPostProcessorTargetCount是PostProcessorRegistrationDelegate的registerBeanPostProcessors方法传入的BeanFactory中
			// 最终BeanPostProcessor的数量，PostProcessorRegistrationDelegate的registerBeanPostProcessors方法在初始化BeanPostProcessor过程中
			// 可能会创建其依赖的bean，而这些被依赖的bean不会被BeanPostProcessor感知到，对于这些bean，这里用日志记录下。
			// 原理是如果创建的bean不是BeanPostProcessor类型的，并且this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount，
			// 说明PostProcessorRegistrationDelegate的registerBeanPostProcessors方法方法还没有执行完（如果执行完了则this.beanFactory.getBeanPostProcessorCount() == this.beanPostProcessorTargetCount）
			// 即BeanPostProcessor还没全部初始化完，此时创建的非BeanPostProcessor类型的bean就需要被记录到日志
			if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
					this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
				if (logger.isInfoEnabled()) {
					logger.info("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
							"] is not eligible for getting processed by all BeanPostProcessors " +
							"(for example: not eligible for auto-proxying)");
				}
			}
			return bean;
		}

		private boolean isInfrastructureBean(@Nullable String beanName) {
			if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
				BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
				return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE);
			}
			return false;
		}
	}

}
