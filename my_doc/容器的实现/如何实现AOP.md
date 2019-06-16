## 如何实现AOP

以AspectJ注解风格的AOP配置为例，XML中添加如下配置:
```
<beans xmlns="http://www.springframework.org/schema/beans"
	   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	   xmlns:aop="http://www.springframework.org/schema/aop"
	   xsi:schemaLocation="http://www.springframework.org/schema/beans
	   http://www.springframework.org/schema/beans/spring-beans.xsd
	   http://www.springframework.org/schema/aop
	   http://www.springframework.org/schema/aop/spring-aop.xsd">
	   
<aop:aspectj-autoproxy/>
<beans>
```
开配置将开启AspectJ注解的自动扫描功能，解析[BeanFactory]中所有代理AspectJ注解的类。当Spring解析XML时，对于aop标签，将会根据自定义命名空间找到指定的[NamespaceHandlerSupport]并进行解析，
而`http://www.springframework.org/schema/aop`命名空间的[NamespaceHandlerSupport]是[AopNamespaceHandler]，[AopNamespaceHandler]注册了多个[BeanDefinitionParser]:
```
registerBeanDefinitionParser("config", new ConfigBeanDefinitionParser());
registerBeanDefinitionParser("aspectj-autoproxy", new AspectJAutoProxyBeanDefinitionParser());
registerBeanDefinitionDecorator("scoped-proxy", new ScopedProxyBeanDefinitionDecorator());

// Only in 2.0 XSD: moved to context namespace as of 2.1
registerBeanDefinitionParser("spring-configured", new SpringConfiguredBeanDefinitionParser());
```
所以上面的`<aop:aspectj-autoproxy/>`将交由[AspectJAutoProxyBeanDefinitionParser]进行解析，[AspectJAutoProxyBeanDefinitionParser]在解析时添加一个[AnnotationAwareAspectJAutoProxyCreator]
的[BeanDefinition]到[BeanFactory]，[AnnotationAwareAspectJAutoProxyCreator]类图如下:
![AnnotationAwareAspectJAutoProxyCreator继承结构图](../img/AnnotationAwareAspectJAutoProxyCreator.png)
[ProxyConfig]类定义了具有创建代理功能的类的通用配置，如`proxyTargetClass`，`exposeProxy`等属性。[ProxyProcessorSupport]类添加`evaluateProxyInterfaces`方法用于判断指定的`bean`是否应该使用`cglib`代理，
[AbstractAutoProxyCreator]实现了基本的创建bean代理的逻辑，获取切面的过程由子类实现，[AbstractAutoProxyCreator]实现了[SmartInstantiationAwareBeanPostProcessor]接口，根据[AbstractAutowireCapableBeanFactory]创建`bean`的过程可知，在创建`bean`时，
[AbstractAutowireCapableBeanFactory]的`createBean`方法会首先遍历[BeanFactory]中的[InstantiationAwareBeanPostProcessor]的`postProcessBeforeInstantiation`方法，如果该方法返回了非空对象则[BeanFactory]将会以该对象作为`bean`返回给客户端，
而[AbstractAutoProxyCreator]类实现了`postProcessBeforeInstantiation`方法，利用[AbstractAutoProxyCreator]的`customTargetSourceCreators`属性尝试创建`bean`的[TargetSource]，如果创建成功则为该[TargetSource]创建代理并作为`bean`返回，
`customTargetSourceCreators`属性配置可以通过配置`bean`指定，如:
```
<bean class="org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator">
	<property name="customTargetSourceCreators">
		<bean class="org.springframework.aop.framework.autoproxy.target.LazyInitTargetSourceCreator"/>
	</property>
</bean>
```
如果没有创建出[TargetSource]，[AbstractAutowireCapableBeanFactory]将会继续进行普通`bean`的创建过程，并在调用完`populateBean`方法设置了`bean`的属性及调用各种初始化方法如各个`Aware`接口和[BeanFactory]中的[BeanPostProcessor]类的`postProcessBeforeInitialization`方法后，
获取[BeanFactory]中的[BeanPostProcessor]并执行`postProcessAfterInitialization`方法，[AbstractAutoProxyCreator]也实现了该方法，代码如下:
```
public Object postProcessAfterInitialization(@Nullable Object bean, String beanName) throws BeansException {
	if (bean != null) {
	    //如果beanName不为空则以beanName为cacheKey，否则以className为cacheKey
		Object cacheKey = getCacheKey(bean.getClass(), beanName);
		//getEarlyBeanReference方法会将bean的cacheKey添加到earlyProxyReferences中，所以这里检查earlyProxyReferences防止对同一个bean多次调用wrapIfNecessary方法
		if (!this.earlyProxyReferences.contains(cacheKey)) {
			return wrapIfNecessary(bean, beanName, cacheKey);
		}
	}
	return bean;
}
```
可以看待代理的主要逻辑在`wrapIfNecessary`方法，代码如下:
```
//如果是需要代理的bean则创建代理，否则直接返回bean
protected Object wrapIfNecessary(Object bean, String beanName, Object cacheKey) {
	//如果创建过bean的targetSource则直接返回，因为该bean已经被代理过了
	if (StringUtils.hasLength(beanName) && this.targetSourcedBeans.contains(beanName)) {
		return bean;
	}
	//如果之前已经判断过该bean不需要代理则直接返回
	if (Boolean.FALSE.equals(this.advisedBeans.get(cacheKey))) {
		return bean;
	}
	//基础设施bean不进行代理
	if (isInfrastructureClass(bean.getClass()) || shouldSkip(bean.getClass(), beanName)) {
		this.advisedBeans.put(cacheKey, Boolean.FALSE);
		return bean;
	}

	// Create proxy if we have advice.
	//获取增强和切面
	Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(bean.getClass(), beanName, null);
	//如果发现了适合当前bean的切面则创建代理
	if (specificInterceptors != DO_NOT_PROXY) {
		this.advisedBeans.put(cacheKey, Boolean.TRUE);
		Object proxy = createProxy(
				bean.getClass(), beanName, specificInterceptors, new SingletonTargetSource(bean));
		this.proxyTypes.put(cacheKey, proxy.getClass());
		return proxy;
	}

	this.advisedBeans.put(cacheKey, Boolean.FALSE);
	return bean;
}
```
`getAdvicesAndAdvisorsForBean`方法获取当前bean的切面和增强(通知)，代码如下:
```
protected Object[] getAdvicesAndAdvisorsForBean(
		Class<?> beanClass, String beanName, @Nullable TargetSource targetSource) {

	//获取当前bean匹配的切面
	List<Advisor> advisors = findEligibleAdvisors(beanClass, beanName);
	if (advisors.isEmpty()) {
		return DO_NOT_PROXY;
	}
	return advisors.toArray();
}

//获取合格的切面
protected List<Advisor> findEligibleAdvisors(Class<?> beanClass, String beanName) {
	//获取所有Advisor类型的bean，并以Advisor类型返回
	List<Advisor> candidateAdvisors = findCandidateAdvisors();
	//遍历获取到的切面，判断是否适用于当前bean
	List<Advisor> eligibleAdvisors = findAdvisorsThatCanApply(candidateAdvisors, beanClass, beanName);
	extendAdvisors(eligibleAdvisors);
	if (!eligibleAdvisors.isEmpty()) {
		eligibleAdvisors = sortAdvisors(eligibleAdvisors);
	}
	return eligibleAdvisors;
}
```
[AnnotationAwareAspectJAutoProxyCreator]重写了`findCandidateAdvisors`方法，在调用父类的`findCandidateAdvisors`之后尝试解析AspectJ风格的AOP配置，代码如下:
```
@Override
protected List<Advisor> findCandidateAdvisors() {
	// Add all the Spring advisors found according to superclass rules.
	//获取Advisor类型的bean
	List<Advisor> advisors = super.findCandidateAdvisors();
	// Build Advisors for all AspectJ aspects in the bean factory.
	//获取AspectJ注解的bean
	if (this.aspectJAdvisorsBuilder != null) {
		advisors.addAll(this.aspectJAdvisorsBuilder.buildAspectJAdvisors());
	}
	return advisors;
}
```
`super.findCandidateAdvisors()`方法将[Advisor]类型的bean直接返回，之后通过`aspectJAdvisorsBuilder`获取AspectJ的AOP配置，`aspectJAdvisorsBuilder`在设置[BeanFactory]时默认设置成[BeanFactoryAspectJAdvisorsBuilderAdapter]，`buildAspectJAdvisors`方法代码如下:
```
public List<Advisor> buildAspectJAdvisors() {
	List<String> aspectNames = this.aspectBeanNames;

	if (aspectNames == null) {
		synchronized (this) {
			aspectNames = this.aspectBeanNames;
			if (aspectNames == null) {
				List<Advisor> advisors = new LinkedList<>();
				aspectNames = new LinkedList<>();
				String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
						this.beanFactory, Object.class, true, false);
				//遍历beanFactory中的所有bean
				for (String beanName : beanNames) {
					if (!isEligibleBean(beanName)) {
						continue;
					}
					// We must be careful not to instantiate beans eagerly as in this case they
					// would be cached by the Spring container but would not have been weaved.
					Class<?> beanType = this.beanFactory.getType(beanName);
					if (beanType == null) {
						continue;
					}
					//如果存在Aspect注解
					if (this.advisorFactory.isAspect(beanType)) {
						aspectNames.add(beanName);
						//AspectMetadata在构造函数中解析了beanType的AjType，对于没有value的AspectJ注解的类，PerClauseKind是SINGLETON
						AspectMetadata amd = new AspectMetadata(beanType, beanName);
						if (amd.getAjType().getPerClause().getKind() == PerClauseKind.SINGLETON) {
							//MetadataAwareAspectInstanceFactory接口只有两个方法，一个返回刚刚创建的AspectMetadata，一个返回
							//锁对象，该锁对象从beanFactory获取
							MetadataAwareAspectInstanceFactory factory =
									new BeanFactoryAspectInstanceFactory(this.beanFactory, beanName);
							//advisorFactory是AnnotationAwareAspectJAutoProxyCreator设置beanFactory是创建的，默认为ReflectiveAspectJAdvisorFactory，
                            //这里用advisorFactory获取指定了Aspect注解的bean的切面
							List<Advisor> classAdvisors = this.advisorFactory.getAdvisors(factory);
							if (this.beanFactory.isSingleton(beanName)) {
								//添加到缓存中下次不用再获取
								this.advisorsCache.put(beanName, classAdvisors);
							}
							else {
								//非单例bean不保存切面缓存，只保存工厂，下次获取时使用工厂重新获取
								this.aspectFactoryCache.put(beanName, factory);
							}
							advisors.addAll(classAdvisors);
						}
						else {
							// Per target or per this.
							if (this.beanFactory.isSingleton(beanName)) {
								throw new IllegalArgumentException("Bean with name '" + beanName +
										"' is a singleton, but aspect instantiation model is not singleton");
							}
							MetadataAwareAspectInstanceFactory factory =
									new PrototypeAspectInstanceFactory(this.beanFactory, beanName);
							this.aspectFactoryCache.put(beanName, factory);
							advisors.addAll(this.advisorFactory.getAdvisors(factory));
						}
					}
				}
				this.aspectBeanNames = aspectNames;
				return advisors;
			}
		}
	}

	if (aspectNames.isEmpty()) {
		return Collections.emptyList();
	}
	List<Advisor> advisors = new LinkedList<>();
	for (String aspectName : aspectNames) {
		List<Advisor> cachedAdvisors = this.advisorsCache.get(aspectName);
		if (cachedAdvisors != null) {
			advisors.addAll(cachedAdvisors);
		}
		else {
			MetadataAwareAspectInstanceFactory factory = this.aspectFactoryCache.get(aspectName);
			advisors.addAll(this.advisorFactory.getAdvisors(factory));
		}
	}
	return advisors;
}
```
对于没有存在[Aspect]注解的类，都会使用[ReflectiveAspectJAdvisorFactory]获取该类的`AspectJ`配置，代码如下:
```
public List<Advisor> getAdvisors(MetadataAwareAspectInstanceFactory aspectInstanceFactory) {
	//返回bean的类型
    Class<?> aspectClass = aspectInstanceFactory.getAspectMetadata().getAspectClass();
    //返回beanName
    String aspectName = aspectInstanceFactory.getAspectMetadata().getAspectName();
    validate(aspectClass);

	// We need to wrap the MetadataAwareAspectInstanceFactory with a decorator
	// so that it will only instantiate once.
	//LazySingletonAspectInstanceFactoryDecorator代理了对象的获取，缓存获取到的对象以保证对象只被初始化一次
	MetadataAwareAspectInstanceFactory lazySingletonAspectInstanceFactory =
			new LazySingletonAspectInstanceFactoryDecorator(aspectInstanceFactory);

	List<Advisor> advisors = new LinkedList<>();
	//获取除了含有Pointcut注解外的其他方法，排序后返回
	for (Method method : getAdvisorMethods(aspectClass)) {
		//创建当前方法的切面
		Advisor advisor = getAdvisor(method, lazySingletonAspectInstanceFactory, advisors.size(), aspectName);
		if (advisor != null) {
			advisors.add(advisor);
		}
	}

	// If it's a per target aspect, emit the dummy instantiating aspect.
	if (!advisors.isEmpty() && lazySingletonAspectInstanceFactory.getAspectMetadata().isLazilyInstantiated()) {
		Advisor instantiationAdvisor = new SyntheticInstantiationAdvisor(lazySingletonAspectInstanceFactory);
		advisors.add(0, instantiationAdvisor);
	}

	// Find introduction fields.
	/*
	获取属性上的DeclareParent注解，DeclareParent注解的作用是为某些带新增实现的接口，如
	@DeclareParents(value = "com.lzj.spring.annotation.Person+", defaultImpl = FemaleAnimal.class)
	public Animal animal;

	上面的注解将会为所有Person类的子类添加一个Animal接口并指定实现为FemaleAnimal
	 */
	for (Field field : aspectClass.getDeclaredFields()) {
		Advisor advisor = getDeclareParentsAdvisor(field);
		if (advisor != null) {
			advisors.add(advisor);
		}
	}

	return advisors;
}
```
`getAdvisor`方法根据传入的[Method]对象返回切面，代码如下:
```
public Advisor getAdvisor(Method candidateAdviceMethod, MetadataAwareAspectInstanceFactory aspectInstanceFactory,
						  int declarationOrderInAspect, String aspectName) {

	validate(aspectInstanceFactory.getAspectMetadata().getAspectClass());

	//创建AspectJExpressionPointcut并设置candidateAdviceMethod上的AspectJ注解的value到AspectJExpressionPointcut，如
	//@Before("test()")的test()
	AspectJExpressionPointcut expressionPointcut = getPointcut(
			candidateAdviceMethod, aspectInstanceFactory.getAspectMetadata().getAspectClass());
	if (expressionPointcut == null) {
		return null;
	}

	//InstantiationModelAwarePointcutAdvisorImpl在构造函数中初始化了Advice(增强、通知)
	return new InstantiationModelAwarePointcutAdvisorImpl(expressionPointcut, candidateAdviceMethod,
			this, aspectInstanceFactory, declarationOrderInAspect, aspectName);
}
```
[InstantiationModelAwarePointcutAdvisorImpl]初始化[Advice]的方法如下:
```
private Advice instantiateAdvice(AspectJExpressionPointcut pointcut) {
	//根据方法上的注解创建不同类型的增强，如Before对应AspectJMethodBeforeAdvice
	Advice advice = this.aspectJAdvisorFactory.getAdvice(this.aspectJAdviceMethod, pointcut,
			this.aspectInstanceFactory, this.declarationOrder, this.aspectName);
	return (advice != null ? advice : EMPTY_ADVICE);
}

public Advice getAdvice(Method candidateAdviceMethod, AspectJExpressionPointcut expressionPointcut,
						MetadataAwareAspectInstanceFactory aspectInstanceFactory, int declarationOrder, String aspectName) {

	Class<?> candidateAspectClass = aspectInstanceFactory.getAspectMetadata().getAspectClass();
	validate(candidateAspectClass);

	AspectJAnnotation<?> aspectJAnnotation =
			AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(candidateAdviceMethod);
	if (aspectJAnnotation == null) {
		return null;
	}

	// If we get here, we know we have an AspectJ method.
	// Check that it's an AspectJ-annotated class
	if (!isAspect(candidateAspectClass)) {
		throw new AopConfigException("Advice must be declared inside an aspect type: " +
				"Offending method '" + candidateAdviceMethod + "' in class [" +
				candidateAspectClass.getName() + "]");
	}

	if (logger.isDebugEnabled()) {
		logger.debug("Found AspectJ method: " + candidateAdviceMethod);
	}

	AbstractAspectJAdvice springAdvice;

	switch (aspectJAnnotation.getAnnotationType()) {
		case AtBefore:
			springAdvice = new AspectJMethodBeforeAdvice(
					candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
			break;
		case AtAfter:
			springAdvice = new AspectJAfterAdvice(
					candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
			break;
		case AtAfterReturning:
			springAdvice = new AspectJAfterReturningAdvice(
					candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
			AfterReturning afterReturningAnnotation = (AfterReturning) aspectJAnnotation.getAnnotation();
			if (StringUtils.hasText(afterReturningAnnotation.returning())) {
				springAdvice.setReturningName(afterReturningAnnotation.returning());
			}
			break;
		case AtAfterThrowing:
			springAdvice = new AspectJAfterThrowingAdvice(
					candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
			AfterThrowing afterThrowingAnnotation = (AfterThrowing) aspectJAnnotation.getAnnotation();
			if (StringUtils.hasText(afterThrowingAnnotation.throwing())) {
				springAdvice.setThrowingName(afterThrowingAnnotation.throwing());
			}
			break;
		case AtAround:
			springAdvice = new AspectJAroundAdvice(
					candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
			break;
		case AtPointcut:
			if (logger.isDebugEnabled()) {
				logger.debug("Processing pointcut '" + candidateAdviceMethod.getName() + "'");
			}
			return null;
		default:
			throw new UnsupportedOperationException(
					"Unsupported advice type on method: " + candidateAdviceMethod);
	}

	// Now to configure the advice...
	springAdvice.setAspectName(aspectName);
	springAdvice.setDeclarationOrder(declarationOrder);
	String[] argNames = this.parameterNameDiscoverer.getParameterNames(candidateAdviceMethod);
	if (argNames != null) {
		springAdvice.setArgumentNamesFromStringArray(argNames);
	}
	springAdvice.calculateArgumentBindings();

	return springAdvice;
}
```
根据从方法注解解析来的类型返回不同的[Advice]，如[Before]注解返回[AspectJMethodBeforeAdvice]。
在获取到所有的[Advisor]后返回到`findEligibleAdvisors`方法，调用`findAdvisorsThatCanApply`过滤不适合当前bean的[Advisor]，最后返回所有合适的[Advisor]到`wrapIfNecessary`方法，
最后根据配置判断使用cglib还是jdk的代理。

[BeanFactory]: aaa
[NamespaceHandlerSupport]: aaa
[AopNamespaceHandler]: aaa
[BeanDefinitionParser]: aaa
[AspectJAutoProxyBeanDefinitionParser]: aaa
[AnnotationAwareAspectJAutoProxyCreator]: aaa
[BeanDefinition]: aaa
[BeanFactory]: aaa
[ProxyConfig]: aaa
[ProxyProcessorSupport]: aaa
[AbstractAutoProxyCreator]: aaa
[SmartInstantiationAwareBeanPostProcessor]: aaa
[AbstractAutowireCapableBeanFactory]: aaa
[InstantiationAwareBeanPostProcessor]: aaa
[Advisor]: aaa
[BeanFactoryAspectJAdvisorsBuilderAdapter]: aaa
[Aspect]: aaa
[ReflectiveAspectJAdvisorFactory]: aaa
[Method]: aaa
[InstantiationModelAwarePointcutAdvisorImpl]: aaa
[Advice]: aaa
[Before]: aaa
[AspectJMethodBeforeAdvice]: aaa
