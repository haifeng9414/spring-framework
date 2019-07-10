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

可以看到，一个线程中获取到的bean是同一个，不同的线程获取到的是不同的bean

