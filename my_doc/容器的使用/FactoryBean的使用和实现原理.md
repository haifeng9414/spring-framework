[FactoryBean]是个接口，实现类本身不作为一个普通bean使用，而是用于创建bean，接口定义如下：
```java
public interface FactoryBean<T> {
	@Nullable
	T getObject() throws Exception;

	@Nullable
	Class<?> getObjectType();

	default boolean isSingleton() {
		return true;
	}
}
```
`getObject()`方法返回[FactoryBean]所创建的bean，[FactoryBean]在容器中和普通单例bean一样保存在容器中，在需要获取其创建的bean时会先获取到[FactoryBean]，再调用其`getObject()`方法返回bean，[FactoryBean]使用例子：
```java
public class MyBean {
	private String id;
	private String name;

	public MyBean(String id, String name) {
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
}

public class MyBeanFactoryBean implements FactoryBean<MyBean> {
	@Override
	public Class<?> getObjectType() {
		return MyBeanA.class;
	}

	@Override
	public MyBean getObject() throws Exception {
		return new MyBean("1", "2");
	}

	@Override
	public boolean isSingleton() {
		return true;
	}
}
```
使用xml定义bean：
```xml
<bean id="myBean" class="org.springframework.tests.sample.beans.MyBeanFactoryBean"/>
```
测试：
```java
public void myTest() {
    ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext(classPathResource("-application-context.xml").getPath(), getClass());
    MyBean myBean1 = applicationContext.getBean("myBean", MyBean.class);
    System.out.println(myBean1.getId());
    System.out.println(myBean1.getClass());
    MyBean myBean2 = applicationContext.getBean("myBean", MyBean.class);
	System.out.println(myBean1.getClass().equals(myBean2.getClass()));
	// 如果想要获取的bean就是FactoryBean而不是其创建的bean，则在beanName前加&
	System.out.println(applicationContext.getBean("&myBean").getClass());
}

/* 
输出:
1
class org.springframework.tests.sample.beans.MyBean
true
class org.springframework.tests.sample.beans.MyBeanFactoryBean
*/
```
上面的测试可以发现，xml中定义的class为[FactoryBean]的实现类MyBeanFactoryBean，当从容器获取myBean时，返回的是MyBeanFactoryBean的`getObject()`方法的返回值，由于MyBeanFactoryBean的`isSingleton()`方法返回true，所以多次获取myBean返回的是同一个MyBean对象

[FactoryBean]的实现原理是，获取到bean之后检查bean是否是[FactoryBean]类型的，如果是则返回其`getObject()`方法的返回值作为，关于容器中bean的创建和获取过程可以看[从容器获取Bean](../容器的实现/从容器获取Bean.md)，检查bean是否是[FactoryBean]类型的过程在[AbstractBeanFactory]的`doGetBean()`方法中，代码：
```java
// 从缓存中获取单例bean，如果存在则返回
Object sharedInstance = getSingleton(beanName);
if (sharedInstance != null && args == null) {
	if (logger.isDebugEnabled()) {
		if (isSingletonCurrentlyInCreation(beanName)) {
			logger.debug("Returning eagerly cached instance of singleton bean '" + beanName +
					"' that is not fully initialized yet - a consequence of a circular reference");
		}
		else {
			logger.debug("Returning cached instance of singleton bean '" + beanName + "'");
		}
	}
	// 如果是FactoryBean的话返回getObject方法的返回值
	bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
}
```
无论是单例bean还是原型bean，返回之前都会执行`getObjectForBeanInstance()`检查是否是[FactoryBean]类型的，`getObjectForBeanInstance()`方法代码：
```java
protected Object getObjectForBeanInstance(
		Object beanInstance, String name, String beanName, @Nullable RootBeanDefinition mbd) {

	// Don't let calling code try to dereference the factory if the bean isn't a factory.
	// 检查name是否是以&开头的，如果是则表示想要获取的就是[FactoryBean]而不是[FactoryBean]创建的bean
	if (BeanFactoryUtils.isFactoryDereference(name)) {
		if (beanInstance instanceof NullBean) {
			return beanInstance;
		}
		// 如果name以&开头但是bean不是FactoryBean类型的则报错
		if (!(beanInstance instanceof FactoryBean)) {
			throw new BeanIsNotAFactoryException(transformedBeanName(name), beanInstance.getClass());
		}
	}

	// Now we have the bean instance, which may be a normal bean or a FactoryBean.
	// If it's a FactoryBean, we use it to create a bean instance, unless the
	// caller actually wants a reference to the factory.
	// 当前bean不是FactoryBean类型的，直接返回，或者如果name是&开头的则表示想要获取的就是FactoryBean，直接返回
	if (!(beanInstance instanceof FactoryBean) || BeanFactoryUtils.isFactoryDereference(name)) {
		return beanInstance;
	}

	// 如果不满足上面的条件则表示beanInstance是FactoryBean类型的并且想要获取的是FactoryBean创建出来的bean，此时调用FactoryBean的
	// getObject方法返回bean
	Object object = null;
	if (mbd == null) {
		// 尝试从缓存中获取beanFactory产生的bean
		object = getCachedObjectForFactoryBean(beanName);
	}
	if (object == null) {
		// Return bean instance from factory.
		FactoryBean<?> factory = (FactoryBean<?>) beanInstance;
		// Caches object obtained from FactoryBean if it is a singleton.
		if (mbd == null && containsBeanDefinition(beanName)) {
			// 合并当前bean和其父bean的属性，即当前bean定义在其他bean内的话，则创建父bean的BeanDefinition并用当前bean的属性覆盖或合并父bean的属性并返回
			// 如果不存在父bean则直接根据当前bean的BeanDefinition创建RootBeanDefinition
			mbd = getMergedLocalBeanDefinition(beanName);
		}
		// synthetic表示bean是否是用户定义的，如果不是则不需要调用postProcessAfterInitialization，如为了支持<aop:config>spring会
		// 创建synthetic为true的bean
		boolean synthetic = (mbd != null && mbd.isSynthetic());
		object = getObjectFromFactoryBean(factory, beanName, !synthetic);
	}
	return object;
}

protected Object getObjectFromFactoryBean(FactoryBean<?> factory, String beanName, boolean shouldPostProcess) {
	/*
	如果FactoryBean是单例的并且FactoryBean对象已经保存到singletonObjects中，即已经添加到单例bean的缓存中
	什么情况下会导致这里的containsSingleton返回false呢，可以看下面关于FactoryBean之间存在循环引用时的分析
	*/
	if (factory.isSingleton() && containsSingleton(beanName)) {
		synchronized (getSingletonMutex()) {
			// 尝试从FactoryBean name --> object的map中获取bean，即从缓存中获取bean
			Object object = this.factoryBeanObjectCache.get(beanName);
			if (object == null) {
				// 调用factory.getObject()方法获取bean
				object = doGetObjectFromFactoryBean(factory, beanName);
				// Only post-process and store if not put there already during getObject() call above
				// (e.g. because of circular reference processing triggered by custom getBean calls)
				Object alreadyThere = this.factoryBeanObjectCache.get(beanName);
				if (alreadyThere != null) {
					object = alreadyThere;
				}
				else {
					// 是否需要执行后置处理
					if (shouldPostProcess) {
						if (isSingletonCurrentlyInCreation(beanName)) {
							// Temporarily return non-post-processed object, not storing it yet..
							return object;
						}
						// 将beanName添加到singletonsCurrentlyInCreation中
						beforeSingletonCreation(beanName);
						try {
							// 遍历BeanPostProcessor调用postProcessAfterInitialization方法
							object = postProcessObjectFromFactoryBean(object, beanName);
						}
						catch (Throwable ex) {
							throw new BeanCreationException(beanName,
									"Post-processing of FactoryBean's singleton object failed", ex);
						}
						finally {
							// 从singletonsCurrentlyInCreation中删除beanName
							afterSingletonCreation(beanName);
						}
					}
					// 以FactoryBean name --> object的形式将bean添加到缓存中
					if (containsSingleton(beanName)) {
						this.factoryBeanObjectCache.put(beanName, object);
					}
				}
			}
			return object;
		}
	}
	else {
		// 不是单例或者FactoryBean还没有注册到singletonObjects的话直接创建新的对象
		Object object = doGetObjectFromFactoryBean(factory, beanName);
		if (shouldPostProcess) {
			try {
				object = postProcessObjectFromFactoryBean(object, beanName);
			}
			catch (Throwable ex) {
				throw new BeanCreationException(beanName, "Post-processing of FactoryBean's object failed", ex);
			}
		}
		return object;
	}
}
```
可以看到对于[FactoryBean]类型的bean的处理并不复杂，就是判断bean是否是[FactoryBean]类型的，如果是调用其`getObejct()`方法，对于单例的[FactoryBean]还会将结果缓存下来防止重复创建

spring是支持单例bean之间的循环引用的，但是如果是单例的[FactoryBean]之间的循环引用，会导致单例bean被创建两次，如：
```java
// 两个普通的bean
public class BeanA {
	private String id;
	private String name;
	private BeanB beanB;

	public BeanA(String id, String name) {
		System.out.println("new BeanA");
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

	public BeanB getBeanB() {
		return beanB;
	}

	public void setBeanB(BeanB beanB) {
		this.beanB = beanB;
	}
}

public class BeanB {
	private String id;
	private String name;
	private BeanA beanA;

	public BeanB(String id, String name) {
		System.out.println("new BeanB");
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

	public BeanA getBeanA() {
		return beanA;
	}

	public void setBeanA(BeanA beanA) {
		this.beanA = beanA;
	}
}

// 两个普通bean对应的FactoryBean
public class FactoryBeanA implements FactoryBean<BeanA> {
	private BeanB beanB;

	@Override
	public BeanA getObject() throws Exception {
		BeanA beanA = new BeanA("1", "beanA");
		beanA.setBeanB(beanB);
		return beanA;
	}

	@Override
	public Class<?> getObjectType() {
		return BeanA.class;
	}

	public BeanB getBeanB() {
		return beanB;
	}

	public void setBeanB(BeanB beanB) {
		this.beanB = beanB;
	}
}

public class FactoryBeanB implements FactoryBean<BeanB> {
	private BeanA beanA;

	@Override
	public BeanB getObject() throws Exception {
		BeanB beanB = new BeanB("2", "beanB");
		beanB.setBeanA(beanA);
		return beanB;
	}

	@Override
	public Class<?> getObjectType() {
		return BeanB.class;
	}

	public BeanA getBeanA() {
		return beanA;
	}

	public void setBeanA(BeanA beanA) {
		this.beanA = beanA;
	}
}

// xml配置，两个FactoryBean互相引用创建的bean
<bean id="beanA" class="org.springframework.tests.sample.beans.FactoryBeanA">
	<property name="beanB" ref="beanB"/>
</bean>

<bean id="beanB" class="org.springframework.tests.sample.beans.FactoryBeanB">
	<property name="beanA" ref="beanA"/>
</bean>
```
上面的简单的单例bean的循环引用的配置，区别只在于xml中配置的bean的class指向了[FactoryBean]，先看测试结果，测试代码：
```java
public void myTest() {
	ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext(classPathResource("-application-context.xml").getPath(), getClass());
	BeanA beanA = applicationContext.getBean("beanA", BeanA.class);
	System.out.println(beanA.getBeanB().getId());
	BeanB beanB = applicationContext.getBean("beanB", BeanB.class);
	System.out.println(beanB.getBeanA().getId());
	System.out.println(beanA.hashCode());
	System.out.println(beanA.getClass());
	System.out.println(beanB.getBeanA().hashCode());
	System.out.println(beanB.getBeanA().getClass());
}

/*
输出：
new BeanA
new BeanB
new BeanA
2
1
879583678
class org.springframework.tests.sample.beans.BeanA
1431530910
class org.springframework.tests.sample.beans.BeanA
*/
```
上面的输出可以发现BeanA被创建了两次，从容器中获取到的BeanA和BeanB的beanA属性是两个不同的BeanA，他们的hashCode不同，结合[容器的初始化过程](../容器的实现/容器的初始化过程.md)和[从容器获取Bean](../容器的实现/从容器获取Bean.md)的内容可以发现导致这个问题的原因，容器启动时默认会初始化所有的单例bean和[FactoryBean]，所以上面xml配置中的FactoryBeanA和FactoryBeanB都会被初始化，另外，如果[FactoryBean]实现类没有实现[SmartFactoryBean]并实现该接口的`isEagerInit()`方法返回true，则只会调用getBean(&beanName)来初始化[FactoryBean]而不触发其`getObject()`方法创建bean，按照解析顺序，FactoryBeanA先初始化，创建单例bean的代码中的某段：
```java
public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
	Assert.notNull(beanName, "Bean name must not be null");
	synchronized (this.singletonObjects) {
		// 先尝试从缓存中获取单例bean
		Object singletonObject = this.singletonObjects.get(beanName);
		if (singletonObject == null) {
			//忽略
			try {
				// 创建单例bean，包括所有的属性注入和init-method都在这一步执行
				singletonObject = singletonFactory.getObject();
				newSingleton = true;
			}
			// 忽略catch
			// 将bean保存到singletonObjects中缓存下来
			if (newSingleton) {
				addSingleton(beanName, singletonObject);
			}
		}
		return singletonObject;
	}
}

protected void addSingleton(String beanName, Object singletonObject) {
	synchronized (this.singletonObjects) {
		this.singletonObjects.put(beanName, singletonObject);
		this.singletonFactories.remove(beanName);
		this.earlySingletonObjects.remove(beanName);
		this.registeredSingletons.add(beanName);
	}
}
```
可以发现只有bean完全创建完成，包括属性都注入完成后才会被添加到singletonObjects缓存起来，而在创建FactoryBeanA的过程中，由于其指定属性引用了beanB，所以在自动注入属性阶段会触发beanB的创建，也就是FactoryBeanB，当创建FactoryBeanB时，又会注入beanA，此时beanA还处于属性注入过程中，如果看过[从容器获取Bean](../容器的实现/从容器获取Bean.md)就知道，此时beanA是以[ObjectFactory]的形式保存在singletonFactories中的，可以通过singletonFactories获取到beanA的[ObjectFactory]从而获取到正在创建的FactoryBeanA，那么在注入beanA到beanB的过程中执行的`getBean(beanA)`是能够获取到beanA的，这也是单例bean的循环引用的工作原理，代码：
```java
// 这里通过singletonFactories获取到了正处于属性注入过程中的beanA，也就是FactoryBeanA的实例
Object sharedInstance = getSingleton(beanName);
if (sharedInstance != null && args == null) {
	if (logger.isDebugEnabled()) {
		if (isSingletonCurrentlyInCreation(beanName)) {
			logger.debug("Returning eagerly cached instance of singleton bean '" + beanName +
					"' that is not fully initialized yet - a consequence of a circular reference");
		}
		else {
			logger.debug("Returning cached instance of singleton bean '" + beanName + "'");
		}
	}
	// 获取FactoryBean的getObject方法的返回值
	bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
}
```
在获取到FactoryBeanA的实例后，会执行`getObjectForBeanInstance(sharedInstance, name, beanName, null)`方法获取其`getObject()`的返回值，这一过程在最开始就分析过了，重点在于执行过程中调用的`getObjectFromFactoryBean()`方法：
```java
protected Object getObjectFromFactoryBean(FactoryBean<?> factory, String beanName, boolean shouldPostProcess) {
	/*
	这里判断了containsSingleton(beanName)，也就是beanName是否存在于单例bean的缓存中
	*/
	if (factory.isSingleton() && containsSingleton(beanName)) {
		synchronized (getSingletonMutex()) {
			// 尝试从FactoryBean name --> object的map中获取bean
			Object object = this.factoryBeanObjectCache.get(beanName);
			if (object == null) {
				// 调用factory.getObject()方法获取bean
				object = doGetObjectFromFactoryBean(factory, beanName);
				// 忽略
				else {
					// 执行postProcess，忽略
					// 以FactoryBean name --> object的形式将bean添加到缓存中
					if (containsSingleton(beanName)) {
						this.factoryBeanObjectCache.put(beanName, object);
					}
				}
			}
			return object;
		}
	}
	else {
		// 不是单例或者FactoryBean还没有注册到singletonObjects的话直接调用FactoryBean的getObject方法创建新的对象
		Object object = doGetObjectFromFactoryBean(factory, beanName);
		if (shouldPostProcess) {
			try {
				object = postProcessObjectFromFactoryBean(object, beanName);
			}
			catch (Throwable ex) {
				throw new BeanCreationException(beanName, "Post-processing of FactoryBean's object failed", ex);
			}
		}
		return object;
	}
}
```
由于beanA还处于属性注入过程中，所以`containsSingleton(beanName)`将返回false，从而导致else语句块被执行，直接调用FactoryBeanA的`getObject()`方法，导致BeanA被第一次创建，并注入到FactoryBeanB中，之后FactoryBeanB完成创建，添加到单例缓存中，并执行`getObjectForBeanInstance(sharedInstance, name, beanName, null)`方法，这触发了FactoryBeanB的`getObject()`方法，导致BeanB的创建，这也是上面测试代码头两行输出的原因，在完成BeanB的创建后，将继续beanA也就是FactoryBeanA的创建，此时属性注入完成，FactoryBeanA也就结束创建并添加到里单例bean的缓存中，由于FactoryBeanA没有实现[SmartFactoryBean]接口，所以
不会触发其`getObject()`方法。

容器启动完成后，测试代码调用了`applicationContext.getBean("beanA", BeanA.class)`获取beanA，这一步同样会执行下面的代码：
```java
protected Object getObjectFromFactoryBean(FactoryBean<?> factory, String beanName, boolean shouldPostProcess) {
	/*
	判断containsSingleton(beanName)，也就是beanName是否存在于单例bean的缓存中
	*/
	if (factory.isSingleton() && containsSingleton(beanName)) {
		synchronized (getSingletonMutex()) {
			// 尝试从FactoryBean name --> object的map中获取bean
			Object object = this.factoryBeanObjectCache.get(beanName);
			if (object == null) {
				// 调用factory.getObject()方法获取bean
				object = doGetObjectFromFactoryBean(factory, beanName);
				// 忽略
				else {
					// 执行postProcess，忽略
					// 以FactoryBean name --> object的形式将bean添加到缓存中
					if (containsSingleton(beanName)) {
						this.factoryBeanObjectCache.put(beanName, object);
					}
				}
			}
			return object;
		}
	}
	else {
		// 不是单例或者FactoryBean还没有注册到singletonObjects的话直接调用FactoryBean的getObject方法创建新的对象
		Object object = doGetObjectFromFactoryBean(factory, beanName);
		if (shouldPostProcess) {
			try {
				object = postProcessObjectFromFactoryBean(object, beanName);
			}
			catch (Throwable ex) {
				throw new BeanCreationException(beanName, "Post-processing of FactoryBean's object failed", ex);
			}
		}
		return object;
	}
}
```
由于FactoryBeanA已经存在与单例bean的缓存中，所以将会执行if代码块，由于第一次创建BeanA执行的是else代码块，所以创建出来的BeanA没有添加到factoryBeanObjectCache中缓存起来，所以此时会再次触发FactoryBeanA的`getObject()`方法创建BeanA，导致BeanA第二次被创建，之后测试代码的输出也都是因为BeanA的两次创建导致的，这明显违背了单例的规定。

针对上面的问题，spring提供了解决方法，实现[FactoryBean]接口时，直接继承抽象类[AbstractFactoryBean]，下面分析[AbstractFactoryBean]的功能和它是如何解决上述单例bean重复创建的问题的。

首先是使用[AbstractFactoryBean]实现[FactoryBean]功能的例子：
```java
// 如果需要循环引用，则需要为bean定义接口
public interface DemoBeanAInterface {
	public String getId();

	public void setId(String id);

	public String getName();

	public void setName(String name);

	public DemoBeanBInterface getDemoBeanB();

	public void setDemoBeanB(DemoBeanBInterface demoBeanB);
}

// 单例bean
public class DemoBeanA implements DemoBeanAInterface {
	private String id;
	private String name;
	private DemoBeanBInterface demoBeanB;

	public DemoBeanA(String id, String name) {
		System.out.println("new DemoBeanA");
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

	@Override
	public DemoBeanBInterface getDemoBeanB() {
		return demoBeanB;
	}

	@Override
	public void setDemoBeanB(DemoBeanBInterface demoBeanB) {
		this.demoBeanB = demoBeanB;
	}
}

// FactoryBean
public class DemoBeanAFactoryBean extends AbstractFactoryBean<DemoBeanAInterface> {
	private DemoBeanBInterface demoBeanBFactoryBean;

	public DemoBeanBInterface getDemoBeanBFactoryBean() {
		return demoBeanBFactoryBean;
	}

	public void setDemoBeanBFactoryBean(DemoBeanBInterface demoBeanBFactoryBean) {
		this.demoBeanBFactoryBean = demoBeanBFactoryBean;
	}

	@Override
	public Class<?> getObjectType() {
		return DemoBeanAInterface.class;
	}

	@Override
	protected DemoBeanAInterface createInstance() throws Exception {
		DemoBeanA demoBeanA = new DemoBeanA("1", "demoBeanA");
		demoBeanA.setDemoBeanB(demoBeanBFactoryBean);
		return demoBeanA;
	}
}

public interface DemoBeanBInterface {
	public String getId();

	public void setId(String id);

	public String getName();

	public void setName(String name);

	public DemoBeanAInterface getDemoBeanA();

	public void setDemoBeanA(DemoBeanAInterface demoBeanA);
}

public class DemoBeanB implements DemoBeanBInterface {
	private String id;
	private String name;
	private DemoBeanAInterface demoBeanA;

	public DemoBeanB(String id, String name) {
		System.out.println("new DemoBeanB");
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

	public DemoBeanAInterface getDemoBeanA() {
		return demoBeanA;
	}

	public void setDemoBeanA(DemoBeanAInterface demoBeanA) {
		this.demoBeanA = demoBeanA;
	}
}

public class DemoBeanBFactoryBean extends AbstractFactoryBean<DemoBeanBInterface> {
	private DemoBeanAInterface demoBeanAFactoryBean;

	public DemoBeanAInterface getDemoBeanAFactoryBean() {
		return demoBeanAFactoryBean;
	}

	public void setDemoBeanAFactoryBean(DemoBeanAInterface demoBeanAFactoryBean) {
		this.demoBeanAFactoryBean = demoBeanAFactoryBean;
	}

	@Override
	public Class<?> getObjectType() {
		return DemoBeanAInterface.class;
	}

	@Override
	protected DemoBeanBInterface createInstance() throws Exception {
		DemoBeanB demoBeanB = new DemoBeanB("2", "demoBeanB");
		demoBeanB.setDemoBeanA(demoBeanAFactoryBean);
		return demoBeanB;
	}
}
```
上面定义了两个接口，两个普通bean和两个继承自[AbstractFactoryBean]抽象类的[FactoryBean]，测试代码：
```java
ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext(classPathResource("-application-context.xml").getPath(), getClass());
DemoBeanA beanA = applicationContext.getBean("demoBeanA", DemoBeanA.class);
System.out.println(beanA.getDemoBeanB().getName());
DemoBeanB beanB = applicationContext.getBean("demoBeanB", DemoBeanB.class);
System.out.println(beanB.getDemoBeanA().getName());
System.out.println(beanA.getDemoBeanB().getClass().getName());
System.out.println(beanB.getDemoBeanA().getClass().getName());

/*
输出：
new DemoBeanB
new DemoBeanA
demoBeanB
demoBeanA
org.springframework.tests.sample.beans.DemoBeanB
com.sun.proxy.$Proxy28
*/
```
可以看到DemoBeanB和DemoBeanA都只创建了一次，不同的地方在于，beanB的demoBeanA指向的是个代理类，这个代理类是[AbstractFactoryBean]中创建的，也是解决循环引用的关键点，[AbstractFactoryBean]代码：
```java
public abstract class AbstractFactoryBean<T>
		implements FactoryBean<T>, BeanClassLoaderAware, BeanFactoryAware, InitializingBean, DisposableBean {

	/** Logger available to subclasses */
	protected final Log logger = LogFactory.getLog(getClass());

	// 默认是单例的，这样bean只会被创建一次，如果不是单例的则每次调用getBean时都会创建一个新的bean
	private boolean singleton = true;

	// 通过BeanClassLoaderAware接口实现自动注入
	@Nullable
	private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

	// 通过BeanFactoryAware接口实现自动注入
	@Nullable
	private BeanFactory beanFactory;

	// 表示是否已经调用过createInstance方法创建bean
	private boolean initialized = false;
	
	@Nullable
	// 通过createInstance方法获取到的单例bean
	private T singletonInstance;

	@Nullable
	// 用于解决循环引用问题，FactoryBean还在创建过程中时，从容器中获取FactoryBean会返回该变量
	private T earlySingletonInstance;


	/**
	 * Set if a singleton should be created, or a new object on each request
	 * otherwise. Default is {@code true} (a singleton).
	 */
	public void setSingleton(boolean singleton) {
		this.singleton = singleton;
	}

	@Override
	public boolean isSingleton() {
		return this.singleton;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}

	@Override
	public void setBeanFactory(@Nullable BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	/**
	 * Return the BeanFactory that this bean runs in.
	 */
	@Nullable
	protected BeanFactory getBeanFactory() {
		return this.beanFactory;
	}

	/**
	 * Obtain a bean type converter from the BeanFactory that this bean
	 * runs in. This is typically a fresh instance for each call,
	 * since TypeConverters are usually <i>not</i> thread-safe.
	 * <p>Falls back to a SimpleTypeConverter when not running in a BeanFactory.
	 * @see ConfigurableBeanFactory#getTypeConverter()
	 * @see org.springframework.beans.SimpleTypeConverter
	 */
	protected TypeConverter getBeanTypeConverter() {
		BeanFactory beanFactory = getBeanFactory();
		if (beanFactory instanceof ConfigurableBeanFactory) {
			return ((ConfigurableBeanFactory) beanFactory).getTypeConverter();
		}
		else {
			return new SimpleTypeConverter();
		}
	}

	/**
	 * Eagerly create the singleton instance, if necessary.
	 */
	@Override
	// afterPropertiesSet方法在注入所有属性，调用完所有的BeanPostProcessor的postProcessBeforeInitialization方法后执行
	public void afterPropertiesSet() throws Exception {
		// 如果是单例的则提前初始化
		if (isSingleton()) {
			this.initialized = true;
			this.singletonInstance = createInstance();
			this.earlySingletonInstance = null;
		}
	}


	/**
	 * Expose the singleton instance or create a new prototype instance.
	 * @see #createInstance()
	 * @see #getEarlySingletonInterfaces()
	 */
	@Override
	public final T getObject() throws Exception {
		if (isSingleton()) {
			// 如果已经初始化过则直接返回即可，否则调用getEarlySingletonInstance返回代理类
			return (this.initialized ? this.singletonInstance : getEarlySingletonInstance());
		}
		else {
			return createInstance();
		}
	}

	/**
	 * Determine an 'eager singleton' instance, exposed in case of a
	 * circular reference. Not called in a non-circular scenario.
	 */
	@SuppressWarnings("unchecked")
	private T getEarlySingletonInstance() throws Exception {
		// 使用的是JDK的动态代理，这里获取需要代理的接口
		Class<?>[] ifcs = getEarlySingletonInterfaces();
		// 如果无法获取到接口则不能创建代理，就无法支持循环引用
		if (ifcs == null) {
			throw new FactoryBeanNotInitializedException(
					getClass().getName() + " does not support circular references");
		}
		if (this.earlySingletonInstance == null) {
			// 创建代理
			this.earlySingletonInstance = (T) Proxy.newProxyInstance(
					this.beanClassLoader, ifcs, new EarlySingletonInvocationHandler());
		}
		return this.earlySingletonInstance;
	}

	/**
	 * Expose the singleton instance (for access through the 'early singleton' proxy).
	 * @return the singleton instance that this FactoryBean holds
	 * @throws IllegalStateException if the singleton instance is not initialized
	 */
	@Nullable
	private T getSingletonInstance() throws IllegalStateException {
		// getSingletonInstance在代理类中用于获取单例bean以执行方法，在初始化之前singletonInstance此时还是null，这里用assert防止发生空指针
		Assert.state(this.initialized, "Singleton instance not initialized yet");
		return this.singletonInstance;
	}

	/**
	 * Destroy the singleton instance, if any.
	 * @see #destroyInstance(Object)
	 */
	@Override
	public void destroy() throws Exception {
		if (isSingleton()) {
			destroyInstance(this.singletonInstance);
		}
	}


	/**
	 * This abstract method declaration mirrors the method in the FactoryBean
	 * interface, for a consistent offering of abstract template methods.
	 * @see org.springframework.beans.factory.FactoryBean#getObjectType()
	 */
	@Override
	@Nullable
	public abstract Class<?> getObjectType();

	/**
	 * Template method that subclasses must override to construct
	 * the object returned by this factory.
	 * <p>Invoked on initialization of this FactoryBean in case of
	 * a singleton; else, on each {@link #getObject()} call.
	 * @return the object returned by this factory
	 * @throws Exception if an exception occurred during object creation
	 * @see #getObject()
	 */
	protected abstract T createInstance() throws Exception;

	/**
	 * Return an array of interfaces that a singleton object exposed by this
	 * FactoryBean is supposed to implement, for use with an 'early singleton
	 * proxy' that will be exposed in case of a circular reference.
	 * <p>The default implementation returns this FactoryBean's object type,
	 * provided that it is an interface, or {@code null} else. The latter
	 * indicates that early singleton access is not supported by this FactoryBean.
	 * This will lead to a FactoryBeanNotInitializedException getting thrown.
	 * @return the interfaces to use for 'early singletons',
	 * or {@code null} to indicate a FactoryBeanNotInitializedException
	 * @see org.springframework.beans.factory.FactoryBeanNotInitializedException
	 */
	@Nullable
	protected Class<?>[] getEarlySingletonInterfaces() {
		Class<?> type = getObjectType();
		return (type != null && type.isInterface() ? new Class<?>[] {type} : null);
	}

	/**
	 * Callback for destroying a singleton instance. Subclasses may
	 * override this to destroy the previously created instance.
	 * <p>The default implementation is empty.
	 * @param instance the singleton instance, as returned by
	 * {@link #createInstance()}
	 * @throws Exception in case of shutdown errors
	 * @see #createInstance()
	 */
	protected void destroyInstance(@Nullable T instance) throws Exception {
	}


	/**
	 * Reflective InvocationHandler for lazy access to the actual singleton object.
	 */
	private class EarlySingletonInvocationHandler implements InvocationHandler {

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if (ReflectionUtils.isEqualsMethod(method)) { // equals直接判断参数和当前代理类是否相等即可
				// Only consider equal when proxies are identical.
				return (proxy == args[0]);
			}
			else if (ReflectionUtils.isHashCodeMethod(method)) { 
				// Use hashCode of reference proxy.
				return System.identityHashCode(proxy);
			}
			else if (!initialized && ReflectionUtils.isToStringMethod(method)) { // toString方法不代理
				return "Early singleton proxy for interfaces " +
						ObjectUtils.nullSafeToString(getEarlySingletonInterfaces());
			}
			try {
				// 剩下的所有方法直接调用单例bean的方法，由于afterPropertiesSet方法在bean创建完成后会调用createInstance方法
				// 创建bean，所以这里的getSingletonInstance能够直接返回已经创建好的单例bean
				return method.invoke(getSingletonInstance(), args); 
			}
			catch (InvocationTargetException ex) {
				throw ex.getTargetException();
			}
		}
	}

}
```
[AbstractFactoryBean]所做的主要工作是在`getObject()`方法中判断是否需要返回代理类，对于循环引用的情况，`getObject()`方法会在`afterPropertiesSet()`方法之前被调用，此时`this.initialized`为false，`getObject()`方法将通过`getEarlySingletonInstance()`方法返回代理类注入到其他bean中，使用的是JDK的动态代理实现的代理，这也是为什么上面的例子中需要创建接口的原因，如果没有循环引用，那么接口就没必要了。

综上所属，[AbstractFactoryBean]解决循环引用的原理就是注入代理类到其他bean，使得[FactoryBean]的循环引用能够像普通bean之间的循环引用一样工作。


[FactoryBean]: aaa
[AbstractBeanFactory]: aaa
[ObjectFactory]: aaa
[SmartFactoryBean]: aaa
[AbstractFactoryBean]: aaa