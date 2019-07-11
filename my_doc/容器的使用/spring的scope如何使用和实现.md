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
	public Object remove(String name) {
		Map<String, Object> scope = this.threadScope.get();
		return scope.remove(name);
	}

	@Override
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

自定义scope的bean创建时会执行其scope的`registerDestructionCallback()`方法注册回调函数，但是Spring不负责该函数的调用，因为自定义scope中bean的生命周期不由Spring控制，Spring仅仅只是调用了`registerDestructionCallback()`方法方法而已，可以参考[SessionScope]的实现，[SessionScope]在`registerDestructionCallback()`方法中将回调函数保存到session，在session完成后调用，完全是自己控制这一过程，同样对于[Scope]接口的`remove()`方法也是，Spring不负责调用该方法，生命时候调用由自定义scope自己控制
