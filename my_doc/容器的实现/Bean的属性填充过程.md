## 如何设置XML中的字符串值到Bean的属性上

属性填充分两步，一步是获取属性值，一步是转换属性值为期望值，对于XML配置文件，属性值一般直接定义在XML中，如：
```java
public class TestPropertyPopulateBean {
	private String name;
	private Integer num;
	private Date time;
	private MyBeanA myBeanA;
	private MyBeanB myBeanB;
	private List<String> stringList;
	private String[] stringArray;
	private Map<String, String> stringMap;

	public TestPropertyPopulateBean(String name, MyBeanA myBeanA) {
		this.name = name;
		this.myBeanA = myBeanA;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Integer getNum() {
		return num;
	}

	public void setNum(Integer num) {
		this.num = num;
	}

	public Date getTime() {
		return time;
	}

	public void setTime(Date time) {
		this.time = time;
	}

	public MyBeanA getMyBeanA() {
		return myBeanA;
	}

	public void setMyBeanA(MyBeanA myBeanA) {
		this.myBeanA = myBeanA;
	}

	public MyBeanB getMyBeanB() {
		return myBeanB;
	}

	public void setMyBeanB(MyBeanB myBeanB) {
		this.myBeanB = myBeanB;
	}

	public List<String> getStringList() {
		return stringList;
	}

	public void setStringList(List<String> stringList) {
		this.stringList = stringList;
	}

	public String[] getStringArray() {
		return stringArray;
	}

	public void setStringArray(String[] stringArray) {
		this.stringArray = stringArray;
	}

	public Map<String, String> getStringMap() {
		return stringMap;
	}

	public void setStringMap(Map<String, String> stringMap) {
		this.stringMap = stringMap;
	}
}

public class MyBeanA {
	private String name;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}

public class MyBeanB {
	private String name;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}

import org.springframework.beans.propertyeditors.CustomDateEditor;
public class CustomDateEditorRegistrar implements PropertyEditorRegistrar {
	@Override
	public void registerCustomEditors(PropertyEditorRegistry registry) {
		registry.registerCustomEditor(Date.class, new CustomDateEditor(new SimpleDateFormat("yyyy-MM-dd"), true));
	}
}
```

XML配置：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
	   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	   xsi:schemaLocation="http://www.springframework.org/schema/beans
	   http://www.springframework.org/schema/beans/spring-beans.xsd">
	<bean id="testPropertyPopulateBean"
			class="org.springframework.tests.sample.beans.property.TestPropertyPopulateBean" autowire="byType">
		<constructor-arg name="name" value="test"/>
		<constructor-arg name="myBeanA" ref="myBeanA"/>
		<property name="num" value="20"/>
		<property name="time" value="2019-06-16"/>
		<property name="stringList">
			<list>
				<value>A</value>
				<value>B</value>
				<value>C</value>
			</list>
		</property>
		<property name="stringArray">
			<array>
				<value>A</value>
				<value>B</value>
				<value>C</value>
			</array>
		</property>
		<property name="stringMap">
			<map>
				<entry key="keyA" value="valueA"/>
				<entry key="keyB" value="valueB"/>
				<entry key="keyC" value="valueC"/>
			</map>
		</property>
		<property name="myBeanA.name" value="tttt"/>
	</bean>

	<bean id="myBeanA" class="org.springframework.tests.sample.beans.property.MyBeanA">
		<property name="name" value="myBeanA"/>
	</bean>

	<bean id="myBeanB" class="org.springframework.tests.sample.beans.property.MyBeanB">
		<property name="name" value="myBeanB"/>
	</bean>

	<bean class="org.springframework.beans.factory.config.CustomEditorConfigurer">
		<property name="propertyEditorRegistrars">
			<bean class="org.springframework.tests.sample.beans.property.CustomDateEditorRegistrar"/>
		</property>
	</bean>
</beans>
```

测试代码：
```java
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

/*
输出：
test
20
Sun Jun 16 00:00:00 CST 2019
tttt
myBeanB
[A, B, C]
[A, B, C]
{keyA=valueA, keyB=valueB, keyC=valueC}
*/
```
上述的例子有构造函数的属性注入和普通成员变量的属性注入，注入的属性有普通的字符串、数字、集合、无法直接转换的Date类型属性和引用其他bean的属性。下面分析spring是如何设置上这些属性的，以下内容假设已经看过笔记[容器的初始化过程](../容器的实现/容器的初始化过程.md)和[从容器获取Bean](../容器的实现/从容器获取Bean.md)

bean的属性注入发生在初始化bean时执行的`populateBean()`方法中，代码：
```java
protected void populateBean(String beanName, RootBeanDefinition mbd, @Nullable BeanWrapper bw) {
	if (bw == null) {
		if (mbd.hasPropertyValues()) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Cannot apply property values to null instance");
		}
		else {
			// Skip property population phase for null instance.
			return;
		}
	}

	// Give any InstantiationAwareBeanPostProcessors the opportunity to modify the
	// state of the bean before properties are set. This can be used, for example,
	// to support styles of field injection.
	boolean continueWithPropertyPopulation = true;

	// 给InstantiationAwareBeanPostProcessors机会在设置属性之前改变bean
	if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
		for (BeanPostProcessor bp : getBeanPostProcessors()) {
			if (bp instanceof InstantiationAwareBeanPostProcessor) {
				InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
				if (!ibp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
					continueWithPropertyPopulation = false;
					break;
				}
			}
		}
	}

	// 如果postProcessAfterInstantiation返回false则停止自动注入属性的过程
	if (!continueWithPropertyPopulation) {
		return;
	}

	// 获取所有从XML的property元素解析到的属性
	// 可能是RuntimeBeanReference或者是TypedStringValue
	PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);

	// 判断属性的注入方式，通过在XML配置bean时设置autowire属性指定，如果设置了autowire则尝试自动注入bean的属性(即使没有在属性上声明注解)
	if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_NAME ||
			mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_TYPE) {
		MutablePropertyValues newPvs = new MutablePropertyValues(pvs);

		// Add property values based on autowire by name if applicable.
		if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_NAME) {
			// 根据属性名称获取bean，获取到的bean保存在newPvs中
			autowireByName(beanName, mbd, bw, newPvs);
		}

		// Add property values based on autowire by type if applicable.
		if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_TYPE) {
			// 根据属性类型获取bean，获取到的bean保存在newPvs中
			autowireByType(beanName, mbd, bw, newPvs);
		}

		pvs = newPvs;
	}

	boolean hasInstAwareBpps = hasInstantiationAwareBeanPostProcessors();
	boolean needsDepCheck = (mbd.getDependencyCheck() != RootBeanDefinition.DEPENDENCY_CHECK_NONE);

	// 如果存在InstantiationAwareBeanPostProcessors并且当前bean没有禁用依赖检查，则执行postProcessPropertyValues方法
	if (hasInstAwareBpps || needsDepCheck) {
		if (pvs == null) {
			pvs = mbd.getPropertyValues();
		}
		PropertyDescriptor[] filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
		if (hasInstAwareBpps) {
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof InstantiationAwareBeanPostProcessor) {
					InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
					pvs = ibp.postProcessPropertyValues(pvs, filteredPds, bw.getWrappedInstance(), beanName);
					if (pvs == null) {
						return;
					}
				}
			}
		}
		if (needsDepCheck) {
			checkDependencies(beanName, mbd, filteredPds, pvs);
		}
	}

	// 设置属性到bean中
	if (pvs != null) {
		applyPropertyValues(beanName, mbd, bw, pvs);
	}
}
```

`populateBean()`方法将属性注入分为两步，一步是获取所有的属性，也就是`PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null)`和该语句下面的`autowireByName()`与`autowireByType`，一步是`applyPropertyValues()`方法，将属性设置到bean中，[PropertyValues]可以在笔记[PropertyValues的实现](属性名解析相关类/PropertyValues的实现.md)中看到

获取到[PropertyValues]后，执行的是autowire逻辑，代码：
```java
protected void populateBean(String beanName, RootBeanDefinition mbd, @Nullable BeanWrapper bw) {
	//...

	// 获取所有从XML的property元素解析到的属性
	// 可能是RuntimeBeanReference或者是TypedStringValue
	PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);

	// 判断属性的注入方式，通过在XML配置bean时设置autowire属性指定，如果设置了autowire则尝试自动注入bean的属性(即使没有在属性上声明注解)
	if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_NAME ||
			mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_TYPE) {
		MutablePropertyValues newPvs = new MutablePropertyValues(pvs);

		// Add property values based on autowire by name if applicable.
		if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_NAME) {
			// 根据属性名称获取bean，获取到的bean保存在newPvs中
			autowireByName(beanName, mbd, bw, newPvs);
		}

		// Add property values based on autowire by type if applicable.
		if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_TYPE) {
			// 根据属性类型获取bean，获取到的bean保存在newPvs中
			autowireByType(beanName, mbd, bw, newPvs);
		}

		pvs = newPvs;
	}

	//...
}
```

如果在bean上声明了autowire属性，则根据配置执行不同的注入逻辑，如根据autowire="byName"，则根据beanName注入，代码：
```java
protected void autowireByName(
		String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {

	// 获取满足自动注入条件的属性名称
	String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
	for (String propertyName : propertyNames) {
		// 如果存在该属性名称的bean则自动注入
		if (containsBean(propertyName)) {
			Object bean = getBean(propertyName);
			// 将自动注入的bean暂时保存在pvs，后面再set到当前创建的bean中
			pvs.add(propertyName, bean);
			// 注册当前bean依赖自动注入的bean的依赖关系
			registerDependentBean(propertyName, beanName);
			if (logger.isDebugEnabled()) {
				logger.debug("Added autowiring by name from bean name '" + beanName +
						"' via property '" + propertyName + "' to bean named '" + propertyName + "'");
			}
		}
		else {
			if (logger.isTraceEnabled()) {
				logger.trace("Not autowiring property '" + propertyName + "' of bean '" + beanName +
						"' by name: no matching bean found");
			}
		}
	}
}
```

`unsatisfiedNonSimpleProperties()`方法用于获取需要自动注入的属性，代码：
```java
// 获取满足自动注入条件的属性名称
protected String[] unsatisfiedNonSimpleProperties(AbstractBeanDefinition mbd, BeanWrapper bw) {
	Set<String> result = new TreeSet<>();
	PropertyValues pvs = mbd.getPropertyValues();
	PropertyDescriptor[] pds = bw.getPropertyDescriptors();
	for (PropertyDescriptor pd : pds) {
		// 满足自动注入的属性需要的条件：存在setter方法、不包含在ignoredDependencyInterfaces和ignoredDependencyTypes中、没有配置在XML的property元素中、不是基础属性如String、Array等
		// ignoredDependencyInterfaces和ignoredDependencyTypes配置在BeanFactory中，AbstractApplicationContext在启动时就为其BeanFactory
		// 添加了多个ignoredDependencyInterface
		if (pd.getWriteMethod() != null && !isExcludedFromDependencyCheck(pd) && !pvs.contains(pd.getName()) &&
				!BeanUtils.isSimpleProperty(pd.getPropertyType())) {
			result.add(pd.getName());
		}
	}
	return StringUtils.toStringArray(result);
}
```

`autowireByName()`方法的逻辑很简单，获取需要注入的属性列表，遍历并根据属性名获取bean，再添加到[MutablePropertyValues]中待后面设置到bean上，`autowireByType()`方法则更复杂点，代码：
```java
protected void  autowireByType(
		String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {

	TypeConverter converter = getCustomTypeConverter();
	if (converter == null) {
		converter = bw;
	}

	Set<String> autowiredBeanNames = new LinkedHashSet<>(4);
	// 获取满足自动注入条件的属性名称
	String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
	for (String propertyName : propertyNames) {
		try {
			/*
			PropertyDescriptor是JDK中的类，通过该类可以读写一个JavaBean的某个属性，常用的构造函数：
			public PropertyDescriptor(String propertyName, Class<?> beanClass)
			public PropertyDescriptor(String propertyName, Class<?> beanClass, String readMethodName, String writeMethodName)
			public PropertyDescriptor(String propertyName, Method readMethod, Method writeMethod)
			*/
			PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
			// Don't try autowiring by type for type Object: never makes sense,
			// even if it technically is a unsatisfied, non-simple property.
			if (Object.class != pd.getPropertyType()) {
				MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);
				// Do not allow eager init for type matching in case of a prioritized post-processor.
				boolean eager = !PriorityOrdered.class.isInstance(bw.getWrappedInstance());
				DependencyDescriptor desc = new AutowireByTypeDependencyDescriptor(methodParam, eager);
				// 获取bean
				Object autowiredArgument = resolveDependency(desc, beanName, autowiredBeanNames, converter);
				if (autowiredArgument != null) {
					// 添加到MutablePropertyValues待后面执行注入
					pvs.add(propertyName, autowiredArgument);
				}
				// 添加被注入的bean和当前bean的依赖关系
				for (String autowiredBeanName : autowiredBeanNames) {
					registerDependentBean(autowiredBeanName, beanName);
					if (logger.isDebugEnabled()) {
						logger.debug("Autowiring by type from bean name '" + beanName + "' via property '" +
								propertyName + "' to bean named '" + autowiredBeanName + "'");
					}
				}
				autowiredBeanNames.clear();
			}
		}
		catch (BeansException ex) {
			throw new UnsatisfiedDependencyException(mbd.getResourceDescription(), beanName, propertyName, ex);
		}
	}
}
```

获取bean的方法是`resolveDependency()`，代码：
```java
@Override
@Nullable
public Object resolveDependency(DependencyDescriptor descriptor, @Nullable String requestingBeanName,
		@Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) throws BeansException {

	// ParameterNameDiscoverer用于获取参数名
	descriptor.initParameterNameDiscovery(getParameterNameDiscoverer());
	// 如果返回值是Optional类型的则用Optional包装返回值
	if (Optional.class == descriptor.getDependencyType()) {
		return createOptionalDependency(descriptor, requestingBeanName);
	}
	// ObjectProvider或ObjectFactory可用于延迟获取bean
	else if (ObjectFactory.class == descriptor.getDependencyType() ||
			ObjectProvider.class == descriptor.getDependencyType()) {
		return new DependencyObjectProvider(descriptor, requestingBeanName);
	}
	else if (javaxInjectProviderClass == descriptor.getDependencyType()) {
		return new Jsr330ProviderFactory().createDependencyProvider(descriptor, requestingBeanName);
	}
	else {
		// 先尝试获取代理
		Object result = getAutowireCandidateResolver().getLazyResolutionProxyIfNecessary(
				descriptor, requestingBeanName);
		// 如果没有代理则解析可用的bean并返回
		if (result == null) {
			// 解析可注入的bean并返回
			result = doResolveDependency(descriptor, requestingBeanName, autowiredBeanNames, typeConverter);
		}
		return result;
	}
}
```

上面的`createOptionalDependency()`方法和[DependencyObjectProvider]类分别处理了需要的类型为Optional和[ObjectProvider]的情况，本质上还是调用的`doResolveDependency()`方法获取bean，`doResolveDependency()`方法代码：
```java
@Nullable
public Object doResolveDependency(DependencyDescriptor descriptor, @Nullable String beanName,
		@Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) throws BeansException {

	InjectionPoint previousInjectionPoint = ConstructorResolver.setCurrentInjectionPoint(descriptor);
	try {
		Object shortcut = descriptor.resolveShortcut(this);
		if (shortcut != null) {
			return shortcut;
		}

		// 获取需要注入的类型
		Class<?> type = descriptor.getDependencyType();
		// 获取可注入的值
		Object value = getAutowireCandidateResolver().getSuggestedValue(descriptor);
		// 如果值不为空则转换后返回
		if (value != null) {
			if (value instanceof String) {
				// 先遍历StringValueResolver看是否需要修改值
				String strVal = resolveEmbeddedValue((String) value);
				BeanDefinition bd = (beanName != null && containsBean(beanName) ? getMergedBeanDefinition(beanName) : null);
				// 调用BeanExpressionResolver再解析一次值
				value = evaluateBeanDefinitionString(strVal, bd);
			}
			// 转换值并返回
			TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
			return (descriptor.getField() != null ?
					converter.convertIfNecessary(value, type, descriptor.getField()) :
					converter.convertIfNecessary(value, type, descriptor.getMethodParameter()));
		}

		// 如果需要注入的属性是array、list、或map则获取所有符合条件的bean并注入到相应类型的返回值中返回
		Object multipleBeans = resolveMultipleBeans(descriptor, beanName, autowiredBeanNames, typeConverter);
		if (multipleBeans != null) {
			return multipleBeans;
		}

		// 否则获取所有符合条件的bean
		Map<String, Object> matchingBeans = findAutowireCandidates(beanName, type, descriptor);
		// 如果没有符合条件的bean则报错
		if (matchingBeans.isEmpty()) {
			if (isRequired(descriptor)) {
				raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor);
			}
			return null;
		}

		String autowiredBeanName;
		Object instanceCandidate;

		// 如果符合条件的bean多余一个则
		if (matchingBeans.size() > 1) {
			// 按照prime属性、Priority注解、beanName和候选beanName是否相等的顺序尝试决定使用哪个bean
			autowiredBeanName = determineAutowireCandidate(matchingBeans, descriptor);
			if (autowiredBeanName == null) {
				// 如果没有找到并且是require的则报错
				if (isRequired(descriptor) || !indicatesMultipleBeans(type)) {
					return descriptor.resolveNotUnique(type, matchingBeans);
				}
				else {
					// In case of an optional Collection/Map, silently ignore a non-unique case:
					// possibly it was meant to be an empty collection of multiple regular beans
					// (before 4.3 in particular when we didn't even look for collection beans).
					return null;
				}
			}
			instanceCandidate = matchingBeans.get(autowiredBeanName);
		}
		else {
			// We have exactly one match.
			Map.Entry<String, Object> entry = matchingBeans.entrySet().iterator().next();
			autowiredBeanName = entry.getKey();
			instanceCandidate = entry.getValue();
		}

		if (autowiredBeanNames != null) {
			autowiredBeanNames.add(autowiredBeanName);
		}
		// 如果最后返回的实例是个类，则按照beanName从BeanFactory中获取bean
		if (instanceCandidate instanceof Class) {
			instanceCandidate = descriptor.resolveCandidate(autowiredBeanName, type, this);
		}
		Object result = instanceCandidate;
		if (result instanceof NullBean) {
			if (isRequired(descriptor)) {
				raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor);
			}
			result = null;
		}
		// 如果将返回的bean和待注入的属性类型不兼容则报错
		if (!ClassUtils.isAssignableValue(type, result)) {
			throw new BeanNotOfRequiredTypeException(autowiredBeanName, type, instanceCandidate.getClass());
		}
		return result;
	}
	finally {
		ConstructorResolver.setCurrentInjectionPoint(previousInjectionPoint);
	}
}
```

在获取到bean之后回到`autowireByType()`方法，`autowireByType()`方法将`resolveDependency()`方法的返回结果添加到[MutablePropertyValues]中返回，待后面注入到bean中。

获取到所有[PropertyValue]后，回到`populateBean()`方法，执行`applyPropertyValues()`方法注入属性，代码：
```java
protected void applyPropertyValues(String beanName, BeanDefinition mbd, BeanWrapper bw, PropertyValues pvs) {
	if (pvs.isEmpty()) {
		return;
	}

	if (System.getSecurityManager() != null && bw instanceof BeanWrapperImpl) {
		((BeanWrapperImpl) bw).setSecurityContext(getAccessControlContext());
	}

	MutablePropertyValues mpvs = null;
	List<PropertyValue> original;

	if (pvs instanceof MutablePropertyValues) {
		mpvs = (MutablePropertyValues) pvs;
		// 如果属性已经转换过了就直接设置
		if (mpvs.isConverted()) {
			// Shortcut: use the pre-converted values as-is.
			try {
				bw.setPropertyValues(mpvs);
				return;
			}
			catch (BeansException ex) {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Error setting property values", ex);
			}
		}
		// 获取所有属性列表
		original = mpvs.getPropertyValueList();
	}
	else {
		original = Arrays.asList(pvs.getPropertyValues());
	}

	TypeConverter converter = getCustomTypeConverter();
	if (converter == null) {
		// BeanWrapperImpl本身就是个TypeConverter
		converter = bw;
	}
	// BeanDefinitionValueResolver用于解析保存在BeanDefinition中的PropertyValue，如RuntimeBeanReference、TypedStringValue或ManagedArray等
	BeanDefinitionValueResolver valueResolver = new BeanDefinitionValueResolver(this, beanName, mbd, converter);

	// Create a deep copy, resolving any references for values.
	List<PropertyValue> deepCopy = new ArrayList<>(original.size());
	boolean resolveNecessary = false;
	for (PropertyValue pv : original) {
		// 如果已经转换过了就直接使用
		if (pv.isConverted()) {
			deepCopy.add(pv);
		}
		else {
			// 获取属性名
			String propertyName = pv.getName();
			// 获取属性值
			Object originalValue = pv.getValue();
			// 解析属性对应的值，如指定ref则返回对应的bean
			Object resolvedValue = valueResolver.resolveValueIfNecessary(pv, originalValue);
			Object convertedValue = resolvedValue;
			boolean convertible = bw.isWritableProperty(propertyName) &&
					!PropertyAccessorUtils.isNestedOrIndexedProperty(propertyName);
			if (convertible) {
				// 将获取到的值转换成需要的属性类型
				convertedValue = convertForProperty(resolvedValue, propertyName, bw, converter);
			}
			// Possibly store converted value in merged bean definition,
			// in order to avoid re-conversion for every created bean instance.
			// 如果转换后的值和原始的值相等则保存到convertedValue中，下次再注入时不用再解析了
			if (resolvedValue == originalValue) {
				if (convertible) {
					pv.setConvertedValue(convertedValue);
				}
				deepCopy.add(pv);
			}
			// 如果原始值是字符串并且不是动态的，并且转换后的值不是集合或者数组，则保存转换后的值，因为这种值解析多次结果都是一样的
			else if (convertible && originalValue instanceof TypedStringValue &&
					!((TypedStringValue) originalValue).isDynamic() &&
					!(convertedValue instanceof Collection || ObjectUtils.isArray(convertedValue))) {
				pv.setConvertedValue(convertedValue);
				deepCopy.add(pv);
			}
			else {
				resolveNecessary = true;
				deepCopy.add(new PropertyValue(pv, convertedValue));
			}
		}
	}
	if (mpvs != null && !resolveNecessary) {
		mpvs.setConverted();
	}

	// Set our (possibly massaged) deep copy.
	try {
		// 设置属性值，即调用setter方法
		bw.setPropertyValues(new MutablePropertyValues(deepCopy));
	}
	catch (BeansException ex) {
		throw new BeanCreationException(
				mbd.getResourceDescription(), beanName, "Error setting property values", ex);
	}
}
```

上面调用了[BeanDefinitionValueResolver]的的`resolveValueIfNecessary()`方法对属性进行解析并获取属性值，代码：
```java
@Nullable
public Object resolveValueIfNecessary(Object argName, @Nullable Object value) {
	// We must check each value to see whether it requires a runtime reference
	// to another bean to be resolved.
	// ref类型的属性将包含在RuntimeBeanReference对象中，这里就直接使用ref的值作为beanName调用getBean返回结果
	if (value instanceof RuntimeBeanReference) {
		RuntimeBeanReference ref = (RuntimeBeanReference) value;
		// 该方法直接调用beanFactory.getBean获取bean
		return resolveReference(argName, ref);
	}
	// RuntimeBeanNameReference是beanName的引用
	else if (value instanceof RuntimeBeanNameReference) {
		String refName = ((RuntimeBeanNameReference) value).getBeanName();
		// doEvaluate方法调用beanExpressionResolver解析refName并返回
		refName = String.valueOf(doEvaluate(refName));
		if (!this.beanFactory.containsBean(refName)) {
			throw new BeanDefinitionStoreException(
					"Invalid bean name '" + refName + "' in bean reference for " + argName);
		}
		// 直接返回解析后的beanName
		return refName;
	}
	// BeanDefinitionHolder表示的是内部bean
	else if (value instanceof BeanDefinitionHolder) {
		// Resolve BeanDefinitionHolder: contains BeanDefinition with name and aliases.
		BeanDefinitionHolder bdHolder = (BeanDefinitionHolder) value;
		// 该方法调用beanFactory的createBean方法创建内部bean
		return resolveInnerBean(argName, bdHolder.getBeanName(), bdHolder.getBeanDefinition());
	}
	// 同上，创建内部bean
	else if (value instanceof BeanDefinition) {
		// Resolve plain BeanDefinition, without contained name: use dummy name.
		BeanDefinition bd = (BeanDefinition) value;
		String innerBeanName = "(inner bean)" + BeanFactoryUtils.GENERATED_BEAN_NAME_SEPARATOR +
				ObjectUtils.getIdentityHexString(bd);
		return resolveInnerBean(argName, innerBeanName, bd);
	}
	// 解析数组类型的属性
	else if (value instanceof ManagedArray) {
		// May need to resolve contained runtime references.
		ManagedArray array = (ManagedArray) value;
		Class<?> elementType = array.resolvedElementType;
		if (elementType == null) {
			// 获取数组元素类型
			String elementTypeName = array.getElementTypeName();
			if (StringUtils.hasText(elementTypeName)) {
				try {
					elementType = ClassUtils.forName(elementTypeName, this.beanFactory.getBeanClassLoader());
					array.resolvedElementType = elementType;
				}
				catch (Throwable ex) {
					// Improve the message by showing the context.
					throw new BeanCreationException(
							this.beanDefinition.getResourceDescription(), this.beanName,
							"Error resolving array type for " + argName, ex);
				}
			}
			else {
				elementType = Object.class;
			}
		}
		// 遍历数组元素并设置到结果集中
		return resolveManagedArray(argName, (List<?>) value, elementType);
	}
	else if (value instanceof ManagedList) {
		// May need to resolve contained runtime references.
		return resolveManagedList(argName, (List<?>) value);
	}
	else if (value instanceof ManagedSet) {
		// May need to resolve contained runtime references.
		return resolveManagedSet(argName, (Set<?>) value);
	}
	else if (value instanceof ManagedMap) {
		// May need to resolve contained runtime references.
		return resolveManagedMap(argName, (Map<?, ?>) value);
	}
	else if (value instanceof ManagedProperties) {
		Properties original = (Properties) value;
		Properties copy = new Properties();
		original.forEach((propKey, propValue) -> {
			if (propKey instanceof TypedStringValue) {
				propKey = evaluate((TypedStringValue) propKey);
			}
			if (propValue instanceof TypedStringValue) {
				propValue = evaluate((TypedStringValue) propValue);
			}
			if (propKey == null || propValue == null) {
				throw new BeanCreationException(
						this.beanDefinition.getResourceDescription(), this.beanName,
						"Error converting Properties key/value pair for " + argName + ": resolved to null");
			}
			copy.put(propKey, propValue);
		});
		return copy;
	}
	// 普通字符串则根据属性类型调用typeConverter转换结果
	else if (value instanceof TypedStringValue) {
		// Convert value to target type here.
		TypedStringValue typedStringValue = (TypedStringValue) value;
		Object valueObject = evaluate(typedStringValue);
		try {
			Class<?> resolvedTargetType = resolveTargetType(typedStringValue);
			if (resolvedTargetType != null) {
				return this.typeConverter.convertIfNecessary(valueObject, resolvedTargetType);
			}
			else {
				return valueObject;
			}
		}
		catch (Throwable ex) {
			// Improve the message by showing the context.
			throw new BeanCreationException(
					this.beanDefinition.getResourceDescription(), this.beanName,
					"Error converting typed String value for " + argName, ex);
		}
	}
	else if (value instanceof NullBean) {
		return null;
	}
	else {
		return evaluate(value);
	}
}
```

对于[TypedStringValue]类型的属性，即普通的字符串的解析，或者其他字符串的解析，如解析[ManagedProperties]类型属性中的value时，在返回字符串值之前还有一步解析表达式的过程，即`resolveValueIfNecessary()`方法中调用的`evaluate()`方法，这一步是为了解析#{foo}这种表达式，具体实现过程可以看笔记[BeanExpressionResolver的实现](属性名解析相关类/BeanExpressionResolver的实现.md)

[PropertyEditorRegistrar]: aaa
[BeanMetadataAttribute]: aaa
[Element]: aaa
[AbstractRefreshableApplicationContext]: aaa
[BeanDefinitionParserDelegate]: aaa
[PropertyValues]: aaa
[PropertyValue]: aaa
[MutablePropertyValues]: aaa
[ObjectProvider]: aaa
[BeanDefinitionValueResolver]: aaa
[TypedStringValue]: aaa
[ManagedProperties]: aaa