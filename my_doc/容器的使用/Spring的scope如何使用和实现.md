## spring的scope如何使用和实现

Spring中的scope用于配置bean的作用域，Spring默认有scope：singleton、prototype、session、request、global session等，这些scope分别定义了不同的bean的生命周期，如scope为request的bean生命周期在一个http request内，xml中定义bean的生命周期通过scope属性设置，如：
```xml
<bean id="requestScopedObject" class="org.springframework.tests.sample.beans.TestBean" scope="request">
    <property name="name" value="test"/>
</bean>
```

想要实现一个自定义的scope需要实现[Scope]接口，[Scope]接口代码：
```java
public interface Scope {

    // 该方法控制bean的创建，是bean生命周期管理的核心，如果认为需要创建一个新的bean，则调用objectFactory.getObject()获取新的bean
	Object get(String name, ObjectFactory<?> objectFactory);

	@Nullable
    // 从当前scope中删除bean
	Object remove(String name);

    // 注册bean销毁的回调函数
	void registerDestructionCallback(String name, Runnable callback);

	@Nullable
	Object resolveContextualObject(String key);

	@Nullable
	String getConversationId();

}
```

实现[Scope]接口后，需要将自定义scope注册到容器中，这里定义一个bean的生命周期在一个线程内的scope，代码如下：
```java
public class SimpleThreadScope implements Scope {

	private static final Log logger = LogFactory.getLog(SimpleThreadScope.class);

	private final ThreadLocal<Map<String, Object>> threadScope =
			new NamedThreadLocal<Map<String, Object>>("SimpleThreadScope") {
				@Override
				protected Map<String, Object> initialValue() {
					return new HashMap<>();
				}
			};


	@Override
	public Object get(String name, ObjectFactory<?> objectFactory) {
		Map<String, Object> scope = this.threadScope.get();
		Object scopedObject = scope.get(name);
		if (scopedObject == null) {
			scopedObject = objectFactory.getObject();
			scope.put(name, scopedObject);
		}
		return scopedObject;
	}

	@Override
	@Nullable
    // 如果实现了registerDestructionCallback方法，则在remove时记得同时要remove注册的回调函数，可以参考SessionScope的实现
	public Object remove(String name) {
		Map<String, Object> scope = this.threadScope.get();
		return scope.remove(name);
	}

	@Override
    // 注册bean的销毁回调函数，当bean被销毁时调用，由于当前scope中bean的生命周期是线程内，
    // 而线程被销毁时并不知道，所以这里没有实现该方法，可以看SessionScope中该方法的实现，SessionScope将销毁回调函数
    // 注册到session，并在session complete时执行销毁函数
	public void registerDestructionCallback(String name, Runnable callback) {
		logger.warn("SimpleThreadScope does not support destruction callbacks. " +
				"Consider using RequestScope in a web environment.");
	}

	@Override
	@Nullable
	public Object resolveContextualObject(String key) {
		return null;
	}

	@Override
	public String getConversationId() {
		return Thread.currentThread().getName();
	}

    // 打印threadScope内容，便于测试时查看threadScope中有哪些bean
    public void printBeans() {
        System.out.println(threadScope.get());
    }
}
```

测试代码：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
	   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	   xsi:schemaLocation="http://www.springframework.org/schema/beans
	   http://www.springframework.org/schema/beans/spring-beans.xsd">


	<bean id="myBeanA" class="org.springframework.tests.sample.beans.MyBeanA" scope="thread">
		<constructor-arg index="0" value="0"/>
		<constructor-arg index="1" value="1"/>
	</bean>
</beans>
```

```java
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

/*
输出：
myBeanA1 == myBeanA2:true
myBeanA3 == myBeanA4:true
myBeanA1 == myBeanA3:false
*/
```

可以看到，一个线程中获取到的bean是同一个，不同的线程获取到的是不同的bean，[SimpleThreadScope]的实现很简单，每次获取bean时判断保存在当前线程的[ThreadLocal]中的Map是否已存在该bean，如果存在则返回，否则调用`objectFactory.getObject()`创建一个新的bean，Spring是如何使用[SimpleThreadScope]实现bean的线程内生命周期的？首先，使用自定义scope需要先将scope注册到[BeanFactory]中，代码：
```java
applicationContext.getBeanFactory().registerScope("thread", new SimpleThreadScope());

// registerScope方法实现在AbstractBeanFactory中
public void registerScope(String scopeName, Scope scope) {
    Assert.notNull(scopeName, "Scope identifier must not be null");
    Assert.notNull(scope, "Scope must not be null");
    // singleton和prototype的scope不可覆盖
    if (SCOPE_SINGLETON.equals(scopeName) || SCOPE_PROTOTYPE.equals(scopeName)) {
        throw new IllegalArgumentException("Cannot replace existing scopes 'singleton' and 'prototype'");
    }
    Scope previous = this.scopes.put(scopeName, scope);
    if (previous != null && previous != scope) {
        if (logger.isInfoEnabled()) {
            logger.info("Replacing scope '" + scopeName + "' from [" + previous + "] to [" + scope + "]");
        }
    }
    else {
        if (logger.isDebugEnabled()) {
            logger.debug("Registering scope '" + scopeName + "' with implementation [" + scope + "]");
        }
    }
}
```

自定义scope保存到了[BeanFactory]的`scopes`中，从笔记[从容器获取Bean](../容器的实现/容器的初始化过程.md)可知，当调用`getBean()`方法获取bean时，会执行[BeanFactory]的`doGetBean()`方法，该方法会判断bean的scope是否是singleton或prototype的，如果不是则作为自定义scope处理，执行下面的代码：
```java
try {
    //...
    if (mbd.isSingleton()) {
        //...
    }
    else if (mbd.isPrototype()) {
        //...
    }
    else {
        String scopeName = mbd.getScope();
        final Scope scope = this.scopes.get(scopeName);
        if (scope == null) {
            throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
        }
        try {
            Object scopedInstance = scope.get(beanName, () -> {
                beforePrototypeCreation(beanName);
                try {
                    return createBean(beanName, mbd, args);
                }
                finally {
                    afterPrototypeCreation(beanName);
                }
            });
            bean = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
        }
        catch (IllegalStateException ex) {
            throw new BeanCreationException(beanName,
                    "Scope '" + scopeName + "' is not active for the current thread; consider " +
                    "defining a scoped proxy for this bean if you intend to refer to it from a singleton",
                    ex);
        }
    }
}
catch (BeansException ex) {
    cleanupAfterBeanCreationFailure(beanName);
    throw ex;
}
```

当bean的scope为自定义scope时，Spring会从`scopes`中获取该scope并调用`get(String name, ObjectFactory<?> objectFactory)`方法获取bean，该方法也就是[SimpleThreadScope]中的`get(String name, ObjectFactory<?> objectFactory)`方法，如果[SimpleThreadScope]调用了`objectFactory.getObject()`方法，则会执行上面的`createBean(beanName, mbd, args)`方法创建新的bean。

自定义scope需要完全控制bean的生命周期，`registerDestructionCallback()`注册回调函数是在[BeanFactory]的`registerDisposableBeanIfNecessary()`方法中调用的，代码：
```java
protected void registerDisposableBeanIfNecessary(String beanName, Object bean, RootBeanDefinition mbd) {
    AccessControlContext acc = (System.getSecurityManager() != null ? getAccessControlContext() : null);
    if (!mbd.isPrototype() && requiresDestruction(bean, mbd)) {
        if (mbd.isSingleton()) {
            // Register a DisposableBean implementation that performs all destruction
            // work for the given bean: DestructionAwareBeanPostProcessors,
            // DisposableBean interface, custom destroy method.
            registerDisposableBean(beanName,
                    new DisposableBeanAdapter(bean, beanName, mbd, getBeanPostProcessors(), acc));
        }
        else {
            // A bean with a custom scope...
            Scope scope = this.scopes.get(mbd.getScope());
            if (scope == null) {
                throw new IllegalStateException("No Scope registered for scope name '" + mbd.getScope() + "'");
            }
            scope.registerDestructionCallback(beanName,
                    new DisposableBeanAdapter(bean, beanName, mbd, getBeanPostProcessors(), acc));
        }
    }
}
```

自定义scope的bean创建时会执行其scope的`registerDestructionCallback()`方法注册回调函数，但是Spring不负责回调函数的调用，因为自定义scope中bean的生命周期不由Spring控制，Spring仅仅只是调用了`registerDestructionCallback()`方法而已，可以参考[SessionScope]的实现，[SessionScope]在`registerDestructionCallback()`方法中将回调函数保存到session，在session完成后调用回调函数，完全是自己控制，同样对于[Scope]接口的`remove()`方法也是，Spring不负责调用该方法，生命时候调用由自定义scope自己控制

实现自定义scope后如果想要将bean从scope从删除，除了手动调用scope的`remove()`方法外，还可以使用代理实现通过bean即可将bean从scope中删除，方法是，定义bean时设置`<aop:scoped-proxy/>`属性，例子如下：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
	   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:aop="http://www.springframework.org/schema/aop"
	   xsi:schemaLocation="http://www.springframework.org/schema/beans
	   http://www.springframework.org/schema/beans/spring-beans.xsd http://www.springframework.org/schema/aop http://www.springframework.org/schema/aop/spring-aop.xsd">
	<bean id="testBean" class="org.springframework.tests.sample.beans.TestBean" scope="thread">
		<property name="name" value="testBean"/>
		<aop:scoped-proxy proxy-target-class="true"/>
	</bean>
</beans>
```

单测：
```java
@Test
public void testScopedProxyThreadScope() {
    ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext(classPathResource("-application-context.xml").getPath(), getClass());
    SimpleThreadScope scope = new SimpleThreadScope();
    applicationContext.getBeanFactory().registerScope("thread", scope);
    TestBean testBean = applicationContext.getBean("testBean", TestBean.class);
    // 获取原始的被代理bean
    TestBean originalTestBean = applicationContext.getBean("scopedTarget.testBean", TestBean.class);
    System.out.println(testBean.getClass());
    System.out.println(originalTestBean.getClass());
    System.out.println(testBean.getName());
    System.out.println(originalTestBean.getName());
    System.out.println(((ScopedObject)testBean).getTargetObject());
    System.out.println(((ScopedObject)testBean).getClass());
    scope.printBeans();
    ((ScopedObject)testBean).removeFromScope();
    scope.printBeans();
}

/*
输出：
class org.springframework.tests.sample.beans.TestBean$$EnhancerBySpringCGLIB$$fad5a0da
class org.springframework.tests.sample.beans.TestBean
testBean
testBean
TestBean{name='testBean'}
class org.springframework.tests.sample.beans.TestBean$$EnhancerBySpringCGLIB$$fad5a0da
{scopedTarget.testBean=TestBean{name='testBean'}}
{}
*/
```

可以看到获取到的TestBean其实是个cglib代理，调用`removeFromScope()`方法前bean被保存在[SimpleThreadScope]中，调用`removeFromScope()`方法后被移除，下面介绍这一过程的实现原理

从笔记[从XML加载Bean配置信息](../容器的实现/从XML加载Bean配置信息.md)可知，`<aop:scoped-proxy/>`属性会被[AopNamespaceHandler]解析，对于`scoped-proxy`属性，[AopNamespaceHandler]调用[ScopedProxyBeanDefinitionDecorator]处理，代码：
```java
class ScopedProxyBeanDefinitionDecorator implements BeanDefinitionDecorator {

	private static final String PROXY_TARGET_CLASS = "proxy-target-class";


	@Override
	public BeanDefinitionHolder decorate(Node node, BeanDefinitionHolder definition, ParserContext parserContext) {
		// 默认为true，即默认使用cglib代理
		boolean proxyTargetClass = true;
		if (node instanceof Element) {
			Element ele = (Element) node;
			if (ele.hasAttribute(PROXY_TARGET_CLASS)) {
				proxyTargetClass = Boolean.valueOf(ele.getAttribute(PROXY_TARGET_CLASS));
			}
		}

		// Register the original bean definition as it will be referenced by the scoped proxy
		// and is relevant for tooling (validation, navigation).
		// 覆盖被代理的bean的BeanDefinition为class为ScopedProxyFactoryBean的BeanDefinition
		BeanDefinitionHolder holder =
				ScopedProxyUtils.createScopedProxy(definition, parserContext.getRegistry(), proxyTargetClass);
		String targetBeanName = ScopedProxyUtils.getTargetBeanName(definition.getBeanName());
		// 发起注册事件
		parserContext.getReaderContext().fireComponentRegistered(
				new BeanComponentDefinition(definition.getBeanDefinition(), targetBeanName));
		return holder;
	}

}
```

[ScopedProxyBeanDefinitionDecorator]类调用`createScopedProxy()`方法创建了一个[BeanDefinition]并覆盖被代理bean的[BeanDefinition]，代码：
```java
public static BeanDefinitionHolder createScopedProxy(BeanDefinitionHolder definition,
        BeanDefinitionRegistry registry, boolean proxyTargetClass) {

    // 被代理的bean的名字
    String originalBeanName = definition.getBeanName();
    // 被代理bean的BeanDefinition
    BeanDefinition targetDefinition = definition.getBeanDefinition();
    // 格式为'scopedTarget.' + beanName
    String targetBeanName = getTargetBeanName(originalBeanName);

    // Create a scoped proxy definition for the original bean name,
    // "hiding" the target bean in an internal target definition.
    // 创建代理类的BeanDefinition，class为ScopedProxyFactoryBean
    RootBeanDefinition proxyDefinition = new RootBeanDefinition(ScopedProxyFactoryBean.class);
    proxyDefinition.setDecoratedDefinition(new BeanDefinitionHolder(targetDefinition, targetBeanName));
    proxyDefinition.setOriginatingBeanDefinition(targetDefinition);
    proxyDefinition.setSource(definition.getSource());
    proxyDefinition.setRole(targetDefinition.getRole());

    // 添加targetBeanName到将要添加的BeanDefinition的PropertyValues中，ScopedProxyFactoryBean类中有该属性
    proxyDefinition.getPropertyValues().add("targetBeanName", targetBeanName);
    if (proxyTargetClass) {
        targetDefinition.setAttribute(AutoProxyUtils.PRESERVE_TARGET_CLASS_ATTRIBUTE, Boolean.TRUE);
        // ScopedProxyFactoryBean's "proxyTargetClass" default is TRUE, so we don't need to set it explicitly here.
    }
    else {
        proxyDefinition.getPropertyValues().add("proxyTargetClass", Boolean.FALSE);
    }

    // Copy autowire settings from original bean definition.
    // 保留targetDefinition的属性注入相关属性
    proxyDefinition.setAutowireCandidate(targetDefinition.isAutowireCandidate());
    proxyDefinition.setPrimary(targetDefinition.isPrimary());
    if (targetDefinition instanceof AbstractBeanDefinition) {
        proxyDefinition.copyQualifiersFrom((AbstractBeanDefinition) targetDefinition);
    }

    // The target bean should be ignored in favor of the scoped proxy.
    targetDefinition.setAutowireCandidate(false);
    targetDefinition.setPrimary(false);

    // Register the target bean as separate bean in the factory.
    // 将被代理的bean的BeanDefinition重新以targetBeanName，也就是'scopedTarget.' + beanName的名字注册一遍
    registry.registerBeanDefinition(targetBeanName, targetDefinition);

    // Return the scoped proxy definition as primary bean definition
    // (potentially an inner bean).
    // 注意这里传入的第二个参数为originalBeanName，该属性将会作为proxyDefinition在beanFactory中的名字，而originalBeanName
    // 就是被代理的bean的名字，所以这里相当于用proxyDefinition覆盖了被代理bean的BeanDefinition
    return new BeanDefinitionHolder(proxyDefinition, originalBeanName, definition.getAliases());
}
```

关键在于[ScopedProxyFactoryBean]类，代码：
```java
@SuppressWarnings("serial")
public class ScopedProxyFactoryBean extends ProxyConfig implements FactoryBean<Object>, BeanFactoryAware {

	/** The TargetSource that manages scoping */
	// SimpleBeanTargetSource在代理中用于返回被代理类，ScopedProxyFactoryBean直接替代了被代理类，没有持有被代理类的实例，
	// 但ScopedProxyBeanDefinitionDecorator在创建代理类的BeanFactory时将被代理类的BeanDefinition以'scopedTarget.' + 被代理bean的名字
	// 的形式重新注册了，所以'scopedTarget.' + 被代理bean的名字就是被代理bean在beanFactory的名字，也就是下面的targetBeanName属性，
	// SimpleBeanTargetSource的实现是以targetBeanName为name，直接从beanFactory获取bean，从而获取到被代理类
	private final SimpleBeanTargetSource scopedTargetSource = new SimpleBeanTargetSource();

	/** The name of the target bean */
	@Nullable
	// 对于scoped-proxy这个例子，targetBeanName = 'scopedTarget.' + 被代理bean的名字
	private String targetBeanName;

	/** The cached singleton proxy */
	@Nullable
	private Object proxy;


	/**
	 * Create a new ScopedProxyFactoryBean instance.
	 */
	public ScopedProxyFactoryBean() {
		setProxyTargetClass(true);
	}


	/**
	 * Set the name of the bean that is to be scoped.
	 */
	public void setTargetBeanName(String targetBeanName) {
		this.targetBeanName = targetBeanName;
		// 保存被代理类的名字到scopedTargetSource
		this.scopedTargetSource.setTargetBeanName(targetBeanName);
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		if (!(beanFactory instanceof ConfigurableBeanFactory)) {
			throw new IllegalStateException("Not running in a ConfigurableBeanFactory: " + beanFactory);
		}
		ConfigurableBeanFactory cbf = (ConfigurableBeanFactory) beanFactory;

		this.scopedTargetSource.setBeanFactory(beanFactory);

		// ProxyFactory用于创建代理
		ProxyFactory pf = new ProxyFactory();
		pf.copyFrom(this);
		// 设置scopedTargetSource为targetSource，scopedTargetSource能够获取到被代理类
		pf.setTargetSource(this.scopedTargetSource);

		Assert.notNull(this.targetBeanName, "Property 'targetBeanName' is required");
		// 对于scoped-proxy这个例子，这里获取的就是被代理类的类型
		Class<?> beanType = beanFactory.getType(this.targetBeanName);
		if (beanType == null) {
			throw new IllegalStateException("Cannot create scoped proxy for bean '" + this.targetBeanName +
					"': Target type could not be determined at the time of proxy creation.");
		}
		if (!isProxyTargetClass() || beanType.isInterface() || Modifier.isPrivate(beanType.getModifiers())) {
			// 使用JDK动态代理，这里设置bean上的所有接口为JDK动态代理将实现的接口
			pf.setInterfaces(ClassUtils.getAllInterfacesForClass(beanType, cbf.getBeanClassLoader()));
		}

		// Add an introduction that implements only the methods on ScopedObject.
		// 创建advice，DelegatingIntroductionInterceptor实现了IntroductionInfo接口，所以addAdvice后scopedObject实现的
		// 接口也会被添加到ProxyFactory的interfaces中，这里相当于在添加advice的同时使代理也实现了ScopedObject接口
		ScopedObject scopedObject = new DefaultScopedObject(cbf, this.scopedTargetSource.getTargetBeanName());
		pf.addAdvice(new DelegatingIntroductionInterceptor(scopedObject));

		// Add the AopInfrastructureBean marker to indicate that the scoped proxy
		// itself is not subject to auto-proxying! Only its target bean is.
		pf.addInterface(AopInfrastructureBean.class);

		// 对于scoped-proxy这个例子，ScopedProxyFactoryBean为自己创建了一个代理，自己实现的FactoryBean接口返回的也是这个代理
		this.proxy = pf.getProxy(cbf.getBeanClassLoader());
	}


	@Override
	public Object getObject() {
		if (this.proxy == null) {
			throw new FactoryBeanNotInitializedException();
		}
		return this.proxy;
	}

	@Override
	public Class<?> getObjectType() {
		if (this.proxy != null) {
			return this.proxy.getClass();
		}
		return this.scopedTargetSource.getTargetClass();
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

}
```

[ScopedProxyFactoryBean]在`setBeanFactory()`方法中创建了一个代理类，并添加了[DelegatingIntroductionInterceptor]为advice，使得[DelegatingIntroductionInterceptor]能够拦截到代理类的方法执行，同时[ScopedProxyFactoryBean]还实现了[FactoryBean]接口，所以从[BeanFactory]中获取TestBean时，返回的是[ScopedProxyFactoryBean]的`getObject()`方法的返回值，也就是其创建的代理，而这个代理只有一个[DelegatingIntroductionInterceptor]的advice，[DelegatingIntroductionInterceptor]创建时传入了[DefaultScopedObject]到构造函数，先看[DefaultScopedObject]的作用，代码：
```java
@SuppressWarnings("serial")
public class DefaultScopedObject implements ScopedObject, Serializable {

	private final ConfigurableBeanFactory beanFactory;

	private final String targetBeanName;


	/**
	 * Creates a new instance of the {@link DefaultScopedObject} class.
	 * @param beanFactory the {@link ConfigurableBeanFactory} that holds the scoped target object
	 * @param targetBeanName the name of the target bean
	 */
	public DefaultScopedObject(ConfigurableBeanFactory beanFactory, String targetBeanName) {
		Assert.notNull(beanFactory, "BeanFactory must not be null");
		Assert.hasText(targetBeanName, "'targetBeanName' must not be empty");
		this.beanFactory = beanFactory;
		this.targetBeanName = targetBeanName;
	}


	@Override
	// 对于scoped-proxy这个例子，返回的将是被代理类
	public Object getTargetObject() {
		return this.beanFactory.getBean(this.targetBeanName);
	}

	@Override
	public void removeFromScope() {
		this.beanFactory.destroyScopedBean(this.targetBeanName);
	}

}
```

可以看到，[DefaultScopedObject]只有两个方法，`getTargetObject()`和`removeFromScope()`，一个返回被代理bean，一个将被代理bean从scope销毁，再看[DelegatingIntroductionInterceptor]的代码：
```java
@SuppressWarnings("serial")
public class DelegatingIntroductionInterceptor extends IntroductionInfoSupport
		implements IntroductionInterceptor {

	@Nullable
	private Object delegate;

	public DelegatingIntroductionInterceptor(Object delegate) {
		init(delegate);
	}
    
	protected DelegatingIntroductionInterceptor() {
		init(this);
	}

	private void init(Object delegate) {
		Assert.notNull(delegate, "Delegate must not be null");
		this.delegate = delegate;
		// 保存delegate实现的所有接口到publishedInterfaces，对于scoped-proxy这个例子，delegate就是DefaultScopedObject
		implementInterfacesOnObject(delegate);

		// We don't want to expose the control interface
		// 忽略下面两个接口
		suppressInterface(IntroductionInterceptor.class);
		suppressInterface(DynamicIntroductionAdvice.class);
	}

	@Override
	@Nullable
	public Object invoke(MethodInvocation mi) throws Throwable {
		// 传入的方法所在类是否在publishedInterfaces中，即是否是被代理的接口
		if (isMethodOnIntroducedInterface(mi)) {
			// Using the following method rather than direct reflection, we
			// get correct handling of InvocationTargetException
			// if the introduced method throws an exception.
			// 直接调用被代理的方法
			Object retVal = AopUtils.invokeJoinpointUsingReflection(this.delegate, mi.getMethod(), mi.getArguments());

			// Massage return value if possible: if the delegate returned itself,
			// we really want to return the proxy.
			// 如果返回结果是delegate，则上面的方法调用返回的是this这个值，此时需要将结果替换为代理
			if (retVal == this.delegate && mi instanceof ProxyMethodInvocation) {
				Object proxy = ((ProxyMethodInvocation) mi).getProxy();
				if (mi.getMethod().getReturnType().isInstance(proxy)) {
					retVal = proxy;
				}
			}
			return retVal;
		}

		// 如果方法不在被代理的接口范围内，则什么也不做，继续其他advice的调用
		return doProceed(mi);
	}
	protected Object doProceed(MethodInvocation mi) throws Throwable {
		// If we get here, just pass the invocation on.
		return mi.proceed();
	}

}
```

[DelegatingIntroductionInterceptor]在构造函数中调用了`init()`方法，该方法将参数`delegate`的所有接口方法保存到了`publishedInterfaces`属性中，而这里的`delegate`就是[DefaultScopedObject]，
[DelegatingIntroductionInterceptor]的`invoke()`方法也很简单，如果调用的方法是`publishedInterfaces`接口中的方法，则直接在`delegate`上调用该方法，否则直接调用`doProceed(mi)`继续执行其他advice，而[ScopedProxyFactoryBean]只添加了一个advice，所以`doProceed(mi)`实际上就是直接调用被代理类的对应方法

根据以上分析，回到单测就可以明白是如何实现的了，单测代码：
```java
public void testScopedProxyThreadScope() {
    ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext(classPathResource("-application-context.xml").getPath(), getClass());
    SimpleThreadScope scope = new SimpleThreadScope();
    applicationContext.getBeanFactory().registerScope("thread", scope);
    // 1
    TestBean testBean = applicationContext.getBean("testBean", TestBean.class);
    // 获取原始的被代理bean
    // 2
    TestBean originalTestBean = applicationContext.getBean("scopedTarget.testBean", TestBean.class);
    // 3
    System.out.println(testBean.getClass());
    // 4
    System.out.println(originalTestBean.getClass());
    // 5
    System.out.println(testBean.getName());
    // 6
    System.out.println(originalTestBean.getName());
    // 7
    System.out.println(((ScopedObject)testBean).getTargetObject());
    // 8
    System.out.println(((ScopedObject)testBean).getClass());
    // 9
    scope.printBeans();
    // 10
    ((ScopedObject)testBean).removeFromScope();
    // 11
    scope.printBeans();
}

/*
输出：
class org.springframework.tests.sample.beans.TestBean$$EnhancerBySpringCGLIB$$fad5a0da
class org.springframework.tests.sample.beans.TestBean
testBean
testBean
TestBean{name='testBean'}
class org.springframework.tests.sample.beans.TestBean$$EnhancerBySpringCGLIB$$fad5a0da
{scopedTarget.testBean=TestBean{name='testBean'}}
{}
*/
```

在1处获取到的实际上是[ScopedProxyFactoryBean]创建的代理，该代理只针对[DefaultScopedObject]类实现的接口的方法进行代理，也就是`getTargetObject()`和`removeFromScope()`，其他方法直接调用被代理类执行，2处通过`scopedTarget.testBean`获取到了被代理类，和[ScopedProxyFactoryBean]的`targetBeanName`是相等的，7处和10处调用的两个方法在[DefaultScopedObject]实现的接口中，也就是保存在了[DelegatingIntroductionInterceptor]的`publishedInterfaces`属性中，所以执行时会被[DelegatingIntroductionInterceptor]处理，直接在其`delegate`属性上执行这两个方法，也就是直接在[DefaultScopedObject]上执行这两个方法，也就解释了单测的输出