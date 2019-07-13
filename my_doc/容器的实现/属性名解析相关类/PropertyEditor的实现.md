[PropertyEditor]是JDK中的类，原用于实现GUI上的字符串和特定类的转换，spring直接用该接口作为字符串和bean属性的转换接口，[PropertyEditor]代码：
```java
public interface PropertyEditor {
    // 设置属性值
    void setValue(Object value);

    // 获取属性值
    Object getValue();

    boolean isPaintable();

    void paintValue(java.awt.Graphics gfx, java.awt.Rectangle box);

    String getJavaInitializationString();

    // 把属性值转换成string
    String getAsText();

    // 把string转换成属性值
    void setAsText(String text) throws java.lang.IllegalArgumentException;

    String[] getTags();

    java.awt.Component getCustomEditor();

    boolean supportsCustomEditor();

    void addPropertyChangeListener(PropertyChangeListener listener);

    void removePropertyChangeListener(PropertyChangeListener listener);

}
```

和属性设置相关的方法只有上面注释的4个方法，其他的都是GUI相关的，可以不用考虑，JDK提供了[PropertyEditorSupport]类来帮助实现[PropertyEditor]接口，代码：
```java
public class PropertyEditorSupport implements PropertyEditor {
    public PropertyEditorSupport() {
        setSource(this);
    }

    public PropertyEditorSupport(Object source) {
        if (source == null) {
           throw new NullPointerException();
        }
        setSource(source);
    }

    // source本意是指向当前PropertyEditor作用的对象的，默认指向PropertyEditor自己，用于记录信息的，不应该在PropertyEditor中修改source
    public Object getSource() {
        return source;
    }

    public void setSource(Object source) {
        this.source = source;
    }

    public void setValue(Object value) {
        this.value = value;
        // 通知监听器值变更事件
        firePropertyChange();
    }

    public Object getValue() {
        return value;
    }

    public boolean isPaintable() {
        return false;
    }

    public void paintValue(java.awt.Graphics gfx, java.awt.Rectangle box) {
    }

    public String getJavaInitializationString() {
        return "???";
    }

    public String getAsText() {
        return (this.value != null)
                ? this.value.toString()
                : null;
    }

    public void setAsText(String text) throws java.lang.IllegalArgumentException {
        if (value instanceof String) {
            setValue(text);
            return;
        }
        // 等待子类实现该方法
        throw new java.lang.IllegalArgumentException(text);
    }

    public String[] getTags() {
        return null;
    }

    public java.awt.Component getCustomEditor() {
        return null;
    }

    public boolean supportsCustomEditor() {
        return false;
    }

    public synchronized void addPropertyChangeListener(
                                PropertyChangeListener listener) {
        if (listeners == null) {
            listeners = new java.util.Vector<>();
        }
        listeners.addElement(listener);
    }

    public synchronized void removePropertyChangeListener(
                                PropertyChangeListener listener) {
        if (listeners == null) {
            return;
        }
        listeners.removeElement(listener);
    }

    public void firePropertyChange() {
        java.util.Vector<PropertyChangeListener> targets;
        synchronized (this) {
            if (listeners == null) {
                return;
            }
            // 浅拷贝当前的监听器，使得后面通知监听器时不用加锁
            targets = unsafeClone(listeners);
        }
        // Tell our listeners that "everything" has changed.
        PropertyChangeEvent evt = new PropertyChangeEvent(source, null, null, null);

        for (int i = 0; i < targets.size(); i++) {
            PropertyChangeListener target = targets.elementAt(i);
            target.propertyChange(evt);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> java.util.Vector<T> unsafeClone(java.util.Vector<T> v) {
        return (java.util.Vector<T>)v.clone();
    }

    private Object value;
    private Object source;
    private java.util.Vector<PropertyChangeListener> listeners;
}
```

继承[PropertyEditorSupport]类，则子类只要实现`getAsText()`和`setAsText()`方法即可，如spring中的[CustomDateEditor]类，代码
```java
public class CustomDateEditor extends PropertyEditorSupport {

	private final DateFormat dateFormat;

	private final boolean allowEmpty;

    // 指定字符串值的大小的精确长度，默认为-1表示不限制
	private final int exactDateLength;

	public CustomDateEditor(DateFormat dateFormat, boolean allowEmpty) {
		this.dateFormat = dateFormat;
		this.allowEmpty = allowEmpty;
		this.exactDateLength = -1;
	}

	public CustomDateEditor(DateFormat dateFormat, boolean allowEmpty, int exactDateLength) {
		this.dateFormat = dateFormat;
		this.allowEmpty = allowEmpty;
		this.exactDateLength = exactDateLength;
	}

	@Override
	public void setAsText(@Nullable String text) throws IllegalArgumentException {
		if (this.allowEmpty && !StringUtils.hasText(text)) {
			// Treat empty String as null value.
			setValue(null);
		}
		else if (text != null && this.exactDateLength >= 0 && text.length() != this.exactDateLength) {
			throw new IllegalArgumentException(
					"Could not parse date: it is not exactly" + this.exactDateLength + "characters long");
		}
		else {
			try {
				setValue(this.dateFormat.parse(text));
			}
			catch (ParseException ex) {
				throw new IllegalArgumentException("Could not parse date: " + ex.getMessage(), ex);
			}
		}
	}

	@Override
	public String getAsText() {
		Date value = (Date) getValue();
		return (value != null ? this.dateFormat.format(value) : "");
	}

}
```

Bean的属性填充过程先获取了bean的[BeanDefinition]的[PropertyValues]，[BeanDefinition]的[PropertyValues]是在容器初始化过程中创建[BeanFactory]后解析资源文件时创建的，对于XML的资源文件，解析并创建[BeanDefinition]的过程在[BeanDefinitionParserDelegate]的`parseBeanDefinitionElement()`方法，代码：
```java
@Nullable
public BeanDefinitionHolder parseBeanDefinitionElement(Element ele, @Nullable BeanDefinition containingBean) {
	// 获取id属性
	String id = ele.getAttribute(ID_ATTRIBUTE);
	// 获取name属性
	String nameAttr = ele.getAttribute(NAME_ATTRIBUTE);

	List<String> aliases = new ArrayList<>();
	// 以,或者;为分隔符，nameAttr属性即指定了beanName也指定了alias，nameAttr也可为空
	if (StringUtils.hasLength(nameAttr)) {
		String[] nameArr = StringUtils.tokenizeToStringArray(nameAttr, MULTI_VALUE_ATTRIBUTE_DELIMITERS);
		aliases.addAll(Arrays.asList(nameArr));
	}

	String beanName = id;
	// 如果id为空则使用name为beanName，否则以id为beanName
	if (!StringUtils.hasText(beanName) && !aliases.isEmpty()) {
		// 以别名的第一个名称为beanName，并将该名称从别名中移除
		beanName = aliases.remove(0);
		if (logger.isDebugEnabled()) {
			logger.debug("No XML 'id' specified - using '" + beanName +
					"' as bean name and " + aliases + " as aliases");
		}
	}

	// 判断beanName和所有的别名是否已经被使用过
	if (containingBean == null) {
		checkNameUniqueness(beanName, aliases, ele);
	}

	// AbstractBeanDefinition类表示XML中的<bean>元素，包含了bean的所有相关信息
	AbstractBeanDefinition beanDefinition = parseBeanDefinitionElement(ele, beanName, containingBean);
	if (beanDefinition != null) {
		// 如果即没有指定id也没有指定name，则beanName为空，这里创建一个别名
		if (!StringUtils.hasText(beanName)) {
			try {
				// 根据Spring的命名规则创建beanName，默认是className + 一些其他字符串，具体可以看generateBeanName方法注释
				if (containingBean != null) {
					beanName = BeanDefinitionReaderUtils.generateBeanName(
							beanDefinition, this.readerContext.getRegistry(), true);
				}
				else {
					// 如果不是内嵌bean则委托给readerContext创建，而readerContext是调用的DefaultBeanNameGenerator创建的
					// DefaultBeanNameGenerator又是调用BeanDefinitionReaderUtils.generateBeanName(beanDefinition, this.readerContext.getRegistry(), false)
					beanName = this.readerContext.generateBeanName(beanDefinition);
					// Register an alias for the plain bean class name, if still possible,
					// if the generator returned the class name plus a suffix.
					// This is expected for Spring 1.2/2.0 backwards compatibility.
					String beanClassName = beanDefinition.getBeanClassName();
					// 如果当前beanName不等于className则新增一个className的别名
					if (beanClassName != null &&
							beanName.startsWith(beanClassName) && beanName.length() > beanClassName.length() &&
							!this.readerContext.getRegistry().isBeanNameInUse(beanClassName)) {
						aliases.add(beanClassName);
					}
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Neither XML 'id' nor 'name' specified - " +
							"using generated bean name [" + beanName + "]");
				}
			}
			catch (Exception ex) {
				error(ex.getMessage(), ele);
				return null;
			}
		}
		String[] aliasesArray = StringUtils.toStringArray(aliases);
		// BeanDefinitionHolder维护了传入构造函数的三个属性，供之后的注册bean信息使用
		return new BeanDefinitionHolder(beanDefinition, beanName, aliasesArray);
	}

	return null;
}

@Nullable
public AbstractBeanDefinition parseBeanDefinitionElement(
		Element ele, String beanName, @Nullable BeanDefinition containingBean) {

	// 保存当前解析状态，用于发生异常时显示解析状态
	this.parseState.push(new BeanEntry(beanName));

	String className = null;
	// 获取className
	if (ele.hasAttribute(CLASS_ATTRIBUTE)) {
		className = ele.getAttribute(CLASS_ATTRIBUTE).trim();
	}
	// 获取parent
	String parent = null;
	if (ele.hasAttribute(PARENT_ATTRIBUTE)) {
		parent = ele.getAttribute(PARENT_ATTRIBUTE);
	}

	try {
		// 初始化AbstractBeanDefinition，包含了parentName和class，创建出来的是GenericBeanDefinition类型，spring中
		// 有3中BeanDefinition，分别是ChildBeanDefinition: 表示存在父bean的BeanDefinition、GenericBeanDefinition: 一站式的BeanDefinition，保存了parentName属性(如果有的话)
		// RootBeanDefinition: 表示普通的bean，这3中BeanDefinition都继承自AbstractBeanDefinition，AbstractBeanDefinition已经实现了大部分BeanDefinition需要的功能，
		// ChildBeanDefinition只是多了校验parentName不为空的逻辑(重写validate方法)，GenericBeanDefinition定义了parentName属性、RootBeanDefinition由一个或多个BeanDefinition
		// 合并而来(子bean合并父bean的属性之后才能作为一个单独的bean)，是容器中一个具体bean的BeanDefinition视图。
		AbstractBeanDefinition bd = createBeanDefinition(className, parent);

		// 根据element获取beanDefinition属性，如autowire、destroyMethod、scope、abstract等所有bean标签上的属性
		parseBeanDefinitionAttributes(ele, beanName, containingBean, bd);
		bd.setDescription(DomUtils.getChildElementValueByTagName(ele, DESCRIPTION_ELEMENT));

		//保存meta数据，meta属性不会影响bean的创建，当需要获取meta时通过beanDefinition.getAttribute(key)获取
		parseMetaElements(ele, bd);
		/*
		解析lookup方法，将该方法的相关属性添加到对象并保存到bd.getMethodOverrides()中，lookup方法代理获取对象的方法，返回一个指定的bean，如
		<lookup-method name="getFruit" bean="apple"/>这样的声明存在于某个bean声明中的话表示该bean的getFruit方法将会返回bean apple，
		常用的使用场景是:
		假设一个单例模式的bean A需要引用另外一个非单例模式的bean B，为了在每次引用的时候都能拿到最新的bean B，可以让bean A通过实现ApplicationContextWare来
		获取applicationContext(即可以获得容器上下文)，从而能在运行时通过ApplicationContext.getBean(String beanName)的方法来获取最新的bean B，
		但是如果用ApplicationContextAware接口，就与Spring代码耦合了，违背了反转控制原则(IoC，即bean完全由Spring容器管理，我们代码只需要用bean就可以了)，
		所以Spring为我们提供了方法注入的方式来实现以上的场景。方法注入方式主要是通过<lookup-method/>标签，Spring通过CGLIB代理了包含lookup-method的bean，
		被代理的方法如上面是getFruit可以是抽象方法，被代理的bean也可以是抽象bean，一般被返回的bean如上面的apple的scope是prototype的，如果是singleton的话每次返回的都是
		同一个bean，那样lookup-method的意义就不大了，当然apple是singleton也不会报错。spring实现lookup-method的地方是在创建bean时AbstractAutowireCapableBeanFactory
		在调用instantiateBean实例化bean的时候判断当前创建的bean是否存在MethodOverrides，如果存在MethodOverrides则使用CGLIB创建代理bean
		*/
		parseLookupOverrideSubElements(ele, bd.getMethodOverrides());
		/*
		解析replaced方法，将该方法的相关属性也是保存到bd.getMethodOverrides()中，replaced方法替代某个方法的执行，如
		<bean id="person" class="test.replaced.Person">
							<replaced-method name="show" replacer="replace"></replaced-method>
				</bean>

				<bean id="replace" class="test.replaced.ReplacedClass"></bean>

				ReplacedClass实现了MethodReplacer接口，该接口只有一个方法，表示将取代其他方法的方法:
				Object reimplement(Object obj, Method method, Object[] args) throws Throwable
		*/
		parseReplacedMethodSubElements(ele, bd.getMethodOverrides());

		// 解析构造函数
		parseConstructorArgElements(ele, bd);
		// 解析property
		parsePropertyElements(ele, bd);
		// 解析qualifier元素，qualifier用于指定注入其他bean时设置需要的bean的名字，这样就能在由多个bean满足注入条件的情况下
		// 选择一个特定的bean
		parseQualifierElements(ele, bd);

		bd.setResource(this.readerContext.getResource());
		bd.setSource(extractSource(ele));

		return bd;
	}
	catch (ClassNotFoundException ex) {
		error("Bean class [" + className + "] not found", ele, ex);
	}
	catch (NoClassDefFoundError err) {
		error("Class that bean class [" + className + "] depends on not found", ele, err);
	}
	catch (Throwable ex) {
		error("Unexpected failure during bean definition parsing", ele, ex);
	}
	finally {
		this.parseState.pop();
	}

	return null;
}
```

解析bean属性的方法在`parsePropertyElements()`方法，代码：
```java
public void parsePropertyElements(Element beanEle, BeanDefinition bd) {
	NodeList nl = beanEle.getChildNodes();
	for (int i = 0; i < nl.getLength(); i++) {
		Node node = nl.item(i);
		if (isCandidateElement(node) && nodeNameEquals(node, PROPERTY_ELEMENT)) {
			parsePropertyElement((Element) node, bd);
		}
	}
}

public void parsePropertyElement(Element ele, BeanDefinition bd) {
	String propertyName = ele.getAttribute(NAME_ATTRIBUTE);
	if (!StringUtils.hasLength(propertyName)) {
		error("Tag 'property' must have a 'name' attribute", ele);
		return;
	}
	// 保存当前解析状态，用于发生异常时显示解析状态
	this.parseState.push(new PropertyEntry(propertyName));
	try {
		if (bd.getPropertyValues().contains(propertyName)) {
			error("Multiple 'property' definitions for property '" + propertyName + "'", ele);
			return;
		}
		// 返回property对应的值，如果是ref的属性则返回RuntimeBeanReference，其他返回TypedStringValue
		Object val = parsePropertyValue(ele, bd, propertyName);
		PropertyValue pv = new PropertyValue(propertyName, val);
		parseMetaElements(ele, pv);
		// 提取source，默认直接返回null(NullSourceExtractor)，另一个实现类PassThroughSourceExtractor返回
		// 传入的Element即这里的ele
		pv.setSource(extractSource(ele));
		// getPropertyValues由AbstractBeanDefinition实现，判断当前BeanDefinition的propertyValues是否为空，
		// 为空则创建一个MutablePropertyValues并返回
		bd.getPropertyValues().addPropertyValue(pv);
	}
	finally {
		this.parseState.pop();
	}
}
```

`parsePropertyElement`方法遍历`<property>`元素，创建对应的[PropertyValue]并添加到[BeanDefinition]的[PropertyValues]中，而[BeanDefinition]的[PropertyValues]默认实现是[MutablePropertyValues]，[BeanDefinition]的`getPropertyValues()`方法代码：
```java
public MutablePropertyValues getPropertyValues() {
	if (this.propertyValues == null) {
		this.propertyValues = new MutablePropertyValues();
	}
	return this.propertyValues;
}
```

上面解析属性值的方法为`parsePropertyValue()`，代码：
```java
@Nullable
public Object parsePropertyValue(Element ele, BeanDefinition bd, @Nullable String propertyName) {
	String elementName = (propertyName != null) ?
					"<property> element for property '" + propertyName + "'" :
					"<constructor-arg> element";

	// Should only have one child element: ref, value, list, etc.
	/*
	对于
	<property name="stringList">
		<list>
			<value>A</value>
			<value>B</value>
			<value>C</value>
		</list>
	</property>
	这种配置，getChildNodes方法返回的就是<property>元素，假设解析的就是上面的配置，这里首先获取<property>元素，之后
	在for循环中获取<property>元素的所有子元素，正常情况下，只会有一个元素，如ref, value, list等

	对于<property name="num" value="20"/>这种配置，getChildNodes方法直接返回<property>元素，并且nl.getLength()等于0
	*/
	NodeList nl = ele.getChildNodes();
	Element subElement = null;
	for (int i = 0; i < nl.getLength(); i++) {
		Node node = nl.item(i);
		if (node instanceof Element && !nodeNameEquals(node, DESCRIPTION_ELEMENT) &&
				!nodeNameEquals(node, META_ELEMENT)) {
			// Child element is what we're looking for.
			// 如果存在多个子元素则报错
			if (subElement != null) {
				error(elementName + " must not contain more than one sub-element", ele);
			}
			else {
				subElement = (Element) node;
			}
		}
	}

	// ref和value至少存在一个，或者存在子元素，如果ref、value或子元素存在其中多余一个，则报错
	boolean hasRefAttribute = ele.hasAttribute(REF_ATTRIBUTE);
	boolean hasValueAttribute = ele.hasAttribute(VALUE_ATTRIBUTE);
	if ((hasRefAttribute && hasValueAttribute) ||
			((hasRefAttribute || hasValueAttribute) && subElement != null)) {
		error(elementName +
				" is only allowed to contain either 'ref' attribute OR 'value' attribute OR sub-element", ele);
	}

	// 如果是rel则使用RuntimeBeanReference代表value，在创建bean的时候通过传入的refName查找bean
	if (hasRefAttribute) {
		String refName = ele.getAttribute(REF_ATTRIBUTE);
		if (!StringUtils.hasText(refName)) {
			error(elementName + " contains empty 'ref' attribute", ele);
		}
		RuntimeBeanReference ref = new RuntimeBeanReference(refName);
		ref.setSource(extractSource(ele));
		return ref;
	}
	// 如果是字符串则使用TypedStringValue封装
	else if (hasValueAttribute) {
		TypedStringValue valueHolder = new TypedStringValue(ele.getAttribute(VALUE_ATTRIBUTE));
		valueHolder.setSource(extractSource(ele));
		return valueHolder;
	}
	// 如果存在子元素则解析子元素
	else if (subElement != null) {
		return parsePropertySubElement(subElement, bd);
	}
	else {
		// Neither child element nor "ref" or "value" attribute found.
		error(elementName + " must specify a ref or value", ele);
		return null;
	}
}
```

对于普通的ref或value属性，直接用[RuntimeBeanReference]和[TypedStringValue]表示，如果存在子元素则需要用`parsePropertySubElement()`方法进一步解析，代码：
```java
@Nullable
public Object parsePropertySubElement(Element ele, @Nullable BeanDefinition bd) {
	return parsePropertySubElement(ele, bd, null);
}

@Nullable
// 解析<property>元素的子元素
public Object parsePropertySubElement(Element ele, @Nullable BeanDefinition bd, @Nullable String defaultValueType) {
	if (!isDefaultNamespace(ele)) {
		return parseNestedCustomElement(ele, bd);
	}
	// 如果是元素名为bean则按照BeanDefinition
	else if (nodeNameEquals(ele, BEAN_ELEMENT)) {
		BeanDefinitionHolder nestedBd = parseBeanDefinitionElement(ele, bd);
		if (nestedBd != null) {
			nestedBd = decorateBeanDefinitionIfRequired(ele, nestedBd, bd);
		}
		return nestedBd;
	}
	// 如果是ref则用RuntimeBeanReference表示
	else if (nodeNameEquals(ele, REF_ELEMENT)) {
		// A generic reference to any name of any bean.
		String refName = ele.getAttribute(BEAN_REF_ATTRIBUTE);
		boolean toParent = false;
		if (!StringUtils.hasLength(refName)) {
			// A reference to the id of another bean in a parent context.
			refName = ele.getAttribute(PARENT_REF_ATTRIBUTE);
			toParent = true;
			if (!StringUtils.hasLength(refName)) {
				error("'bean' or 'parent' is required for <ref> element", ele);
				return null;
			}
		}
		if (!StringUtils.hasText(refName)) {
			error("<ref> element contains empty target attribute", ele);
			return null;
		}
		RuntimeBeanReference ref = new RuntimeBeanReference(refName, toParent);
		ref.setSource(extractSource(ele));
		return ref;
	}
	// idref和ref一样，也用RuntimeBeanNameReference表示
	else if (nodeNameEquals(ele, IDREF_ELEMENT)) {
		return parseIdRefElement(ele);
	}
	// value元素用TypedStringValue表示
	else if (nodeNameEquals(ele, VALUE_ELEMENT)) {
		return parseValueElement(ele, defaultValueType);
	}
	else if (nodeNameEquals(ele, NULL_ELEMENT)) {
		// It's a distinguished null value. Let's wrap it in a TypedStringValue
		// object in order to preserve the source location.
		TypedStringValue nullHolder = new TypedStringValue(null);
		nullHolder.setSource(extractSource(ele));
		return nullHolder;
	}
	// array元素用ManagedArray表示
	else if (nodeNameEquals(ele, ARRAY_ELEMENT)) {
		return parseArrayElement(ele, bd);
	}
	// list元素用ManagedList表示
	else if (nodeNameEquals(ele, LIST_ELEMENT)) {
		return parseListElement(ele, bd);
	}
	// set元素用ManagedSet表示
	else if (nodeNameEquals(ele, SET_ELEMENT)) {
		return parseSetElement(ele, bd);
	}
	// map元素用ManagedMap表示
	else if (nodeNameEquals(ele, MAP_ELEMENT)) {
		return parseMapElement(ele, bd);
	}
	// props元素用ManagedProperties表示
	else if (nodeNameEquals(ele, PROPS_ELEMENT)) {
		return parsePropsElement(ele);
	}
	else {
		error("Unknown property sub-element: [" + ele.getNodeName() + "]", ele);
		return null;
	}
}
```

解析array、list等元素的过程是一样的，以array元素的解析过程为例，代码：
```java
public Object parseArrayElement(Element arrayEle, @Nullable BeanDefinition bd) {
	// 从value-type属性获取元素值的类型，可以为空
	String elementType = arrayEle.getAttribute(VALUE_TYPE_ATTRIBUTE);
	NodeList nl = arrayEle.getChildNodes();
	// 以ManagedArray保存数组类型的属性
	ManagedArray target = new ManagedArray(elementType, nl.getLength());
	target.setSource(extractSource(arrayEle));
	target.setElementTypeName(elementType);
	// merge属性表示如果存在parent beanDefinition，则parent beanDefinition的同名的array元素是否需要合并到当前array元素中
	target.setMergeEnabled(parseMergeAttribute(arrayEle));
	// 解析集合子元素
	parseCollectionElements(nl, target, bd, elementType);
	return target;
}

protected void parseCollectionElements(
		NodeList elementNodes, Collection<Object> target, @Nullable BeanDefinition bd, String defaultElementType) {

	// 遍历子元素并递归调用parsePropertySubElement方法解析
	for (int i = 0; i < elementNodes.getLength(); i++) {
		Node node = elementNodes.item(i);
		if (node instanceof Element && !nodeNameEquals(node, DESCRIPTION_ELEMENT)) {
			target.add(parsePropertySubElement((Element) node, bd, defaultElementType));
		}
	}
}
```

[PropertyEditor]: aaa