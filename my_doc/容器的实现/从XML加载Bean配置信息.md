## 从XML加载Bean配置信息

加载Bean的配置信息有很多中方法，如从XML解析、根据注解解析等，下面主要介绍根据XML文件解析Bean配置信息。

Spring中从XML文件解析Bean配置的信息的默认实现是[XmlBeanDefinitionReader]，该类的构造函数接收[BeanDefinitionRegistry]类为参数，
[BeanDefinitionRegistry]用于Bean信息即[BeanDefinition]的增删改查，默认实现是[DefaultListableBeanFactory]，最简单的Spring容器测试代码如下:

```java
DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
new XmlBeanDefinitionReader(factory).loadBeanDefinitions(PATH_TO_XML);
```

即通过[XmlBeanDefinitionReader]解析XML并将解析到的Bean注册到[BeanDefinitionRegistry]，一般不会直接使用[DefaultListableBeanFactory]，而是使用一个[ApplicationContext]实例，[BeanFactory]提供的高级配置机制，使得管理任何性质的对象成为可能，而[ApplicationContext]是[BeanFactory]的扩展，功能得到了进一步增强，比如更易与Spring AOP集成、消息资源处理(国际化处理)、事件传递及各种不同应用层的context实现(如针对web应用的[WebApplicationContext])，同时也是开箱即用，只需要设置资源文件的位置就可以了，例如，在web应用程序中，只需要在web.xml中添加简单的XML描述符即可。如下使用ContextLoaderListener来注册一个[ApplicationContext]：

```xml
<context-param>
       <param-name>contextConfigLocation</param-name>
       <param-value>/WEB-INF/daoContext.xml /WEB-INF/applicationContext.xml</param-value>
</context-param>

<listener>
       <listener-class>org.springframework.web.context.ContextLoaderListener</listener-class>
</listener>
<!-- or use the ContextLoaderServlet instead of the above listener
<servlet>
       <servlet-name>context</servlet-name>
       <servlet-class>org.springframework.web.context.ContextLoaderServlet</servlet-class>
       <load-on-startup>1</load-on-startup>
</servlet>
-->
```

下面用[ClassPathXmlApplicationContext]举例说明[ApplicationContext]的工作过程，[ClassPathXmlApplicationContext]创建和使用方式如下:

```java
ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext("xxxx.xml");
MyBeanA myBeanA = applicationContext.getBean("myBeanA", MyBeanA.class);
```

[ClassPathXmlApplicationContext]继承结构如下:
![ClassPathXmlApplicationContext继承结构图](../../img/ClassPathXmlApplicationContext.png)

下面分析解析XML文件并加载Bean的配置信息到容器的过程：

1. [ClassPathXmlApplicationContext]的构造函数根据传入的path创建[ClassPathResource]对象，代码如下：
   ```java
   public ClassPathXmlApplicationContext(String[] paths, Class<?> clazz, @Nullable ApplicationContext parent)
			throws BeansException {

		super(parent);
		Assert.notNull(paths, "Path array must not be null");
		Assert.notNull(clazz, "Class argument must not be null");
		this.configResources = new Resource[paths.length];
		for (int i = 0; i < paths.length; i++) {
			this.configResources[i] = new ClassPathResource(paths[i], clazz);
		}
		refresh();
	}
   ```
   [ClassPathXmlApplicationContext]重写了`getConfigResources()`方法，返回构造函数中创建的[ClassPathResource]对象数组，[ClassPathResource]对象表示当前[ApplicationContext]需要解析的资源，可以返回类路径下资源的URL和File。[ClassPathXmlApplicationContext]的构造函数在最后调用了`refresh()`方法开始创建容器，`refresh()`方法的实现在其父类[AbstractApplicationContext]
2. `refresh()`方法包含了所有容器创建过程中需要执行的操作，这些过程可以看笔记[容器的初始化过程](容器的初始化过程.md)，对于Bean配置信息的加载，在`refresh()`方法中调用的`obtainFreshBeanFactory()`方法，代码：
   ```java
   protected ConfigurableListableBeanFactory obtainFreshBeanFactory() {
		refreshBeanFactory();
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		if (logger.isDebugEnabled()) {
			logger.debug("Bean factory for " + getDisplayName() + ": " + beanFactory);
		}
		return beanFactory;
	}
   ```
   `refreshBeanFactory()`方法实现在[AbstractRefreshableApplicationContext]，代码：
   ```java
   protected final void refreshBeanFactory() throws BeansException {
		if (hasBeanFactory()) {
			destroyBeans();
			closeBeanFactory();
		}
		try {
			// BeanFactory的默认实现是DefaultListableBeanFactory
			DefaultListableBeanFactory beanFactory = createBeanFactory();
                     beanFactory.setSerializationId(getId());
                     // AbstractRefreshableApplicationContext在该方法中判断用户是否设置了allowBeanDefinitionOverriding或allowCircularReferences
                     // 属性，如果设置了则更新beanFactory相应的设置，AbstractRefreshableApplicationContext的子类也可以重写该方法定制化beanFactory
			customizeBeanFactory(beanFactory);
			// 加载XML配置
			loadBeanDefinitions(beanFactory);
			synchronized (this.beanFactoryMonitor) {
				this.beanFactory = beanFactory;
			}
		}
		catch (IOException ex) {
			throw new ApplicationContextException("I/O error parsing bean definition source for " + getDisplayName(), ex);
		}
	}
   ```
   容器的`refresh()`方法可以被重复调用，所以`refreshBeanFactory()`方法判断了[BeanFactory]是否已存在，存在则销毁。

3. Bean配置信息的加载在`loadBeanDefinitions()`方法中，该方法是个抽象方法，容器的实现可以重写该方法实现自己的Bean配置信息加载逻辑，[ClassPathXmlApplicationContext]用XML配置Bean的信息，其父类[AbstractXmlApplicationContext]实现了该方法，代码：
   ```java
   protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) throws BeansException, IOException {
		// Create a new XmlBeanDefinitionReader for the given BeanFactory.
		XmlBeanDefinitionReader beanDefinitionReader = new XmlBeanDefinitionReader(beanFactory);

		// Configure the bean definition reader with this context's
              // resource loading environment.
              // 设置environment用于支持XML中的profile属性
		beanDefinitionReader.setEnvironment(this.getEnvironment());
		// 容器本身就是个ResourceLoader，能够根据location返回Resource对象
		beanDefinitionReader.setResourceLoader(this);
		// EntityResolver的作用是提供一个寻找DTD声明的方式。如果sax应用程序实现自定义处理外部实体,则必须实现此接口，
		// 并使用setEntityResolver方法向sax驱动器注册一个实例。对于解析一个xml，sax首先会读取该xml文档上的声明，
		// 根据声明去寻找相应的dtd定义，以便对文档的进行验证，默认的寻找规则是通过网络，实现上就是声明DTD的地址URI地址来下载DTD声明并进行认证，
		// 下载的过程是一个漫长的过程，而且当网络不可用时会报错，就是因为相应的dtd没找到。
		// EntityResolver的作用就是项目本身就可以提供一个如何寻找DTD的声明方法，由程序来实现寻找DTD声明的过程，
		// 比如将DTD放在项目的某处在实现时直接将此文档读取并返回给sax即可，这样就避免了通过网络来寻找DTD的声明。
		// spring具体实现EntityResolver接口的方法直接看ResourceEntityResolver及其父类的注释
		beanDefinitionReader.setEntityResolver(new ResourceEntityResolver(this));

		// Allow a subclass to provide custom initialization of the reader,
		// then proceed with actually loading the bean definitions.
		// 对beanDefinitionReader做一些定制化的配置
		initBeanDefinitionReader(beanDefinitionReader);
		loadBeanDefinitions(beanDefinitionReader);
	}
   ```
   `loadBeanDefinitions()`方法首先以传入的[DefaultListableBeanFactory]作为[BeanDefinitionRegistry]对象来初始化[XmlBeanDefinitionReader]，并为[XmlBeanDefinitionReader]对象设置[EntityResolver]，初始化完[XmlBeanDefinitionReader]对象后再调用`loadBeanDefinitions()`的重载方法，传入[XmlBeanDefinitionReader]对象开始解析XML并加载Bean定义
4. 重载的`loadBeanDefinitions()`方法代码：
   ```java
   protected void loadBeanDefinitions(XmlBeanDefinitionReader reader) throws BeansException, IOException {
		Resource[] configResources = getConfigResources();
		if (configResources != null) {
			reader.loadBeanDefinitions(configResources);
		}
		String[] configLocations = getConfigLocations();
		if (configLocations != null) {
			reader.loadBeanDefinitions(configLocations);
		}
	}
   ```
   调用`getConfigResources()`方法（第一步中[ClassPathXmlApplicationContext]对象实现了该方法，返回其构造函数中创建的[ClassPathResource]对象）获取需要解析的[Resource]对象数组，之后传入[Resource]对象数组到[XmlBeanDefinitionReader]对象的`loadBeanDefinitions()`方法进行解析
5. [XmlBeanDefinitionReader]对象遍历传入的[Resource]数组，依次调用`loadBeanDefinitions()`方法解析，在解析[Resource]对象前将[Resource]对象封装为[EncodedResource]对象，代码：
   ```java
   // 返回从传入的Resource对象解析到的BeanDefinition对象个数
   public int loadBeanDefinitions(Resource resource) throws BeanDefinitionStoreException {
	return loadBeanDefinitions(new EncodedResource(resource));
   }
       
   public int loadBeanDefinitions(EncodedResource encodedResource) throws BeanDefinitionStoreException {
	Assert.notNull(encodedResource, "EncodedResource must not be null");
	if (logger.isInfoEnabled()) {
		logger.info("Loading XML bean definitions from " + encodedResource.getResource());
	}

	// 保存当前线程正在处理的resource，防止循环引用
	Set<EncodedResource> currentResources = this.resourcesCurrentlyBeingLoaded.get();
	if (currentResources == null) {
		currentResources = new HashSet<>(4);
		this.resourcesCurrentlyBeingLoaded.set(currentResources);
	}
	if (!currentResources.add(encodedResource)) {
		throw new BeanDefinitionStoreException(
				"Detected cyclic loading of " + encodedResource + " - check your import definitions!");
	}
	try {
		InputStream inputStream = encodedResource.getResource().getInputStream();
		try {
			InputSource inputSource = new InputSource(inputStream);
			if (encodedResource.getEncoding() != null) {
				inputSource.setEncoding(encodedResource.getEncoding());
			}
			return doLoadBeanDefinitions(inputSource, encodedResource.getResource());
		}
		finally {
			inputStream.close();
		}
	}
	catch (IOException ex) {
		throw new BeanDefinitionStoreException(
				"IOException parsing XML document from " + encodedResource.getResource(), ex);
	}
	finally {
		currentResources.remove(encodedResource);
		if (currentResources.isEmpty()) {
			this.resourcesCurrentlyBeingLoaded.remove();
		}
	}
   }
   ```
   [EncodedResource]对象支持指定encoding或charset读取[Resource]资源。[XmlBeanDefinitionReader]对象根据[EncodedResource]对象创建`InputSource`（`javax.xml`包下的类，该包专门用于解析XML文件），最后调用`doLoadBeanDefinitions()`方法解析Bean配置信息。
6. `doLoadBeanDefinitions()`只是简单的创建`Document`对象（也是`javax.xml`包下的类），再调用`registerBeanDefinitions()`方法解析Bean配置信息，代码：
   ```java
   protected int doLoadBeanDefinitions(InputSource inputSource, Resource resource)
			throws BeanDefinitionStoreException {
   	try {
   		Document doc = doLoadDocument(inputSource, resource);
   		return registerBeanDefinitions(doc, resource);
   	} ...catch省略
   }
   ```
7. `registerBeanDefinitions()`方法代码：
   ```java
   public int registerBeanDefinitions(Document doc, Resource resource) throws BeanDefinitionStoreException {
   	// 单一责任原则，XmlBeanDefinitionReader已经从resource获取到了Document了，从Document读取BeanDefinition的工作就交给BeanDefinitionDocumentReader了
   	BeanDefinitionDocumentReader documentReader = createBeanDefinitionDocumentReader();
       int countBefore = getRegistry().getBeanDefinitionCount();
       // createReaderContext返回XmlReaderContext对象，包含XmlBeanDefinitionReader的某些组件，使documentReader解析Bean配置信息时能够使用这些组件
   	documentReader.registerBeanDefinitions(doc, createReaderContext(resource));
   	return getRegistry().getBeanDefinitionCount() - countBefore;
   }
   ```
   `Document`到[BeanDefinition]的解析，[XmlBeanDefinitionReader]对象委托给了[BeanDefinitionDocumentReader]，默认实现是[DefaultBeanDefinitionDocumentReader]，[XmlBeanDefinitionReader]调用该对象的`registerBeanDefinitions()`方法，传入`Document`对象注册[BeanDefinition]，代码：
   ```java
   public void registerBeanDefinitions(Document doc, XmlReaderContext readerContext) {
		this.readerContext = readerContext;
		logger.debug("Loading bean definitions");
		Element root = doc.getDocumentElement();
		doRegisterBeanDefinitions(root);
	}
   ```

8. `registerBeanDefinitions()`方法获取XML的`root element`并开始解析，代码：
   ```java
   protected void doRegisterBeanDefinitions(Element root) {
              // <bean>标签可以包含内嵌的<bean>，而内嵌的<bean>的解析会递归的调用doRegisterBeanDefinitions方法，所以这里记录下每个
              // BeanDefinitionParserDelegate的parent，以支持内嵌的<bean>使用其父<bean>的默认属性
		BeanDefinitionParserDelegate parent = this.delegate;
		this.delegate = createDelegate(getReaderContext(), root, parent);

		//过滤掉profile不等于当前environment中激活的profile的资源
		if (this.delegate.isDefaultNamespace(root)) {
			String profileSpec = root.getAttribute(PROFILE_ATTRIBUTE);
			if (StringUtils.hasText(profileSpec)) {
				String[] specifiedProfiles = StringUtils.tokenizeToStringArray(
						profileSpec, BeanDefinitionParserDelegate.MULTI_VALUE_ATTRIBUTE_DELIMITERS);
				if (!getReaderContext().getEnvironment().acceptsProfiles(specifiedProfiles)) {
					if (logger.isInfoEnabled()) {
						logger.info("Skipped XML bean definition file due to specified profiles [" + profileSpec +
								"] not matching: " + getReaderContext().getResource());
					}
					return;
				}
			}
		}

		//空方法、供子类实现
              preProcessXml(root);
              // 解析Bean配置信息
		parseBeanDefinitions(root, this.delegate);
		//空方法、供子类实现
		postProcessXml(root);

              // 回溯
		this.delegate = parent;
	}
   ```
   `doRegisterBeanDefinitions()`方法过滤掉了未被激活的配置，创建并设置[BeanDefinitionParserDelegate]对象的parent属性，[BeanDefinitionParserDelegate]对象提供解析XML文件的工具方法，如`isDefaultNamespace()`方法用于判断当前XML文件是否是默认的命名空间，还提供了XML文件中不同元素的解析功能，如`import`、`alias`、`bean`等，解析Bean配置信息的逻辑又转到了`parseBeanDefinitions()`方法
9. `parseBeanDefinitions()`方法代码：
   ```java
   protected void parseBeanDefinitions(Element root, BeanDefinitionParserDelegate delegate) {
		if (delegate.isDefaultNamespace(root)) {
			NodeList nl = root.getChildNodes();
			for (int i = 0; i < nl.getLength(); i++) {
				Node node = nl.item(i);
				if (node instanceof Element) {
					Element ele = (Element) node;
					// 如果是默认的命名空间则使用默认的处理方法
					if (delegate.isDefaultNamespace(ele)) {
						parseDefaultElement(ele, delegate);
					}
                                   /*
                                   否则用自定义的处理方式，spring自己就使用了自定义标签，如<tx:annotation-driven>，当解析到该元素时spring会
					作为自定义标签处理，可以看spring-tx模块中的实现，想要实现一个自定义标签的处理需要做的工作有:
					1.新建一个xsd文件，如spring-tx/src/main/resources/org/springframework/transaction/config/spring-tx.xsd文件，用于校验xml
					2.实现BeanDefinitionParser接口，如AnnotationDrivenBeanDefinitionParser类，用于xml解析
					3.实现一个NamespaceHandler继承自NamespaceHandlerSupport，如TxNamespaceHandler，用于注册自定义的BeanDefinitionParser
					4.编写spring.handlers和spring.schemas文件，如spring.handlers添加如下内容:
						http\://www.springframework.org/schema/tx=org.springframework.transaction.config.TxNamespaceHandler
						spring.schemas添加如下内容:
						http\://www.springframework.org/schema/tx/spring-tx.xsd=org/springframework/transaction/config/spring-tx.xsd
					参考例子:spring-tx/src/main/resources/META-INF/spring.handlers和spring-tx/src/main/resources/META-INF/spring.schemas
					xml配置文件中使用方式如下:
					在xml中引入自定义命名空间: xmlns:tx="http://www.springframework.org/schema/tx"
                                   之后在xml写<tx:annotation-driven>，spring就会寻找tx对应的命名空间，即http://www.springframework.org/schema/tx对应的NamespaceHandler，并在tx指定的命名空间对应的NamespaceHandler中找annotation-driven对应的BeanDefinitionParser进行解析。
					在命名空间下引入xsd，如:
                                   http://www.springframework.org/schema/tx  http://www.springframework.org/schema/tx/spring-tx-4.0.xsd
                                   上面的配置表示http://www.springframework.org/schema/tx命名空间的xsd文件在http://www.springframework.org/schema/tx/spring-tx-4.0.xsd
					整个xml配置头信息:
					<beans xmlns="http://www.springframework.org/schema/beans" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    				              xmlns:tx="http://www.springframework.org/schema/tx" <!--这一行引入命名空间-->
    				              xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-4.0.xsd
    				              http://www.springframework.org/schema/tx  http://www.springframework.org/schema/tx/spring-tx-4.0.xsd"> <!--这一行表示http://www.springframework.org/schema/tx的xsd文件在http://www.springframework.org/schema/tx/spring-tx-4.0.xsd-->
					*/
					else {
						delegate.parseCustomElement(ele);
					}
				}
			}
		}
		else {
			delegate.parseCustomElement(root);
		}
	}
   ``` 
   `parseBeanDefinitions()`方法获取`root element`的所有子元素并遍历，如果是默认命名空间的元素，如`<bean>`，则调用`parseDefaultElement()`方法解析，如果是自定义元素，如Spring自定义的`<tx:annotation-driven>`元素，则调用`parseCustomElement()`方法解析。
10. 对默认命名空间的解析在`parseDefaultElement()`方法，代码：
    ```java
    private void parseDefaultElement(Element ele, BeanDefinitionParserDelegate delegate) {
		// 解析import元素，原理就是获取资源并调用getReaderContext().getReader().loadBeanDefinitions()方法
		if (delegate.nodeNameEquals(ele, IMPORT_ELEMENT)) {
			importBeanDefinitionResource(ele);
		}
		// 解析别名
		else if (delegate.nodeNameEquals(ele, ALIAS_ELEMENT)) {
			processAliasRegistration(ele);
		}
		// 解析bean
		else if (delegate.nodeNameEquals(ele, BEAN_ELEMENT)) {
			processBeanDefinition(ele, delegate);
		}
		// 解析beans，原理是重新以<beans>元素为root元素开始解析
		else if (delegate.nodeNameEquals(ele, NESTED_BEANS_ELEMENT)) {
			// recurse
			doRegisterBeanDefinitions(ele);
		}
	}
    ```
    [DefaultBeanDefinitionDocumentReader]对象利用[BeanDefinitionParserDelegate]对象获取元素的结点名，再根据结点名调用[BeanDefinitionParserDelegate]对象的不同方法解析XML文件，而对于[BeanDefinition]的解析注册主要就在`processBeanDefinition()`方法
11. `processBeanDefinition()`方法代码：
    ```java
    protected void processBeanDefinition(Element ele, BeanDefinitionParserDelegate delegate) {
		// 委托BeanDefinitionParserDelegate获取bdHolder，bdHolder包含了bean的所有配置，如class、id、alias等
		BeanDefinitionHolder bdHolder = delegate.parseBeanDefinitionElement(ele);
		if (bdHolder != null) {
			/*
			如果存在自定义的子元素，如下，则需要调用相应的NamespaceHandler处理
			<bean id="myBeanA" class="org.springframework.tests.sample.beans.MyBeanA">
				<mybean:user username='aaa'/>
			</bean>
			中的mybean
			 */
			bdHolder = delegate.decorateBeanDefinitionIfRequired(ele, bdHolder);
			try {
				// Register the final decorated instance.
				//注册bean
				BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, getReaderContext().getRegistry());
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error("Failed to register bean definition with name '" +
						bdHolder.getBeanName() + "'", ele, ex);
			}
			// Send registration event.
			getReaderContext().fireComponentRegistered(new BeanComponentDefinition(bdHolder));
		}
	}
    ```
    `processBeanDefinition()`方法首先调用[BeanDefinitionParserDelegate]对象的`parseBeanDefinitionElement()`方法获取[BeanDefinitionHolder]，该对象包含了bean的所有配置，如class、id、alias等，创建过程：
    ```java
    public BeanDefinitionHolder parseBeanDefinitionElement(Element ele) {
		return parseBeanDefinitionElement(ele, null);
	}

	/**
	 * Parses the supplied {@code <bean>} element. May return {@code null}
	 * if there were errors during parse. Errors are reported to the
	 * {@link org.springframework.beans.factory.parsing.ProblemReporter}.
	 */
	@Nullable
	public BeanDefinitionHolder parseBeanDefinitionElement(Element ele, @Nullable BeanDefinition containingBean) {
		/*
		一个普通的<bean>配置
		<bean id="myBeanA" class="org.springframework.tests.sample.beans.MyBeanA" autowire="byType">
			<constructor-arg value="1"/>
			<constructor-arg value="2"/>
			<property name="age" value="100"/>
		</bean>
		 */

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
			// 如果即没有指定id也没有指定name，则beanName为空，那就需要生成一个别名
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
    ```
12. `parseBeanDefinitionElement()`方法解析了Bean的名称和别名，而Bean的配置信息则交由`parseBeanDefinitionElement()`方法的重载版本完成的，代码：
    ```java
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
13. 创建完[BeanDefinitionHolder]后，回到`processBeanDefinition()`方法：
    ```java
    protected void processBeanDefinition(Element ele, BeanDefinitionParserDelegate delegate) {
		BeanDefinitionHolder bdHolder = delegate.parseBeanDefinitionElement(ele);
		if (bdHolder != null) {
                     /*
			如果存在自定义的子元素，如下，则需要调用相应的NamespaceHandler处理
			<bean id="myBeanA" class="org.springframework.tests.sample.beans.MyBeanA">
				<mybean:user username='aaa'/>
			</bean>
			中的mybean
			 */
			bdHolder = delegate.decorateBeanDefinitionIfRequired(ele, bdHolder);
			try {
				// Register the final decorated instance.
				BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, getReaderContext().getRegistry());
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error("Failed to register bean definition with name '" +
						bdHolder.getBeanName() + "'", ele, ex);
			}
			// Send registration event.
			getReaderContext().fireComponentRegistered(new BeanComponentDefinition(bdHolder));
		}
	}
    ```
    在注册Bean配置信息前调用了`decorateBeanDefinitionIfRequired()`方法，该方法用于解析`<bean>`元素内的自定义元素，实现原理就是根据自定义元素名获取[NamespaceHandler]并交由该[NamespaceHandler]解析自定义元素，这和解析普通的自定义元素的过程是一样的，这里就不赘述了。
14. 获取到[BeanDefinitionHolder]后调用[BeanDefinitionReaderUtils]的`registerBeanDefinition()`方法注册Bean配置信息，代码：
    ```java
    public static void registerBeanDefinition(
			BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry registry)
			throws BeanDefinitionStoreException {

		// Register bean definition under primary name.
		String beanName = definitionHolder.getBeanName();
		// 以bean的名字进行注册
		registry.registerBeanDefinition(beanName, definitionHolder.getBeanDefinition());

		// Register aliases for bean name, if any.
		String[] aliases = definitionHolder.getAliases();
		if (aliases != null) {
			for (String alias : aliases) {
				// 以别名进行注册
				registry.registerAlias(beanName, alias);
			}
		}
	}
    ```
    传入的`registry`实际上就是[DefaultListableBeanFactory]，[DefaultListableBeanFactory]内部用Map维护beanName和[BeanDefinition]的对应关系，这也就是Bean配置信息的注册过程。

[BeanDefinitionRegistry]: aaa
[DefaultListableBeanFactory]: aaa
[WebApplicationContext]: aaa
[ApplicationContext]: aaa
[ClassPathResource]: aaa
[Resource]: aaa
[EncodedResource]: aaa
[BeanDefinitionDocumentReader]: aaa
[DefaultBeanDefinitionDocumentReader]: aaa
[BeanDefinition]: aaa
[BeanDefinitionParserDelegate]: aaa
[BeanDefinitionHolder]: aaa
[PropertySources]: aaa
[EntityResolver]: aaa
[ResourceEntityResolver]: aaa
[ClassPathXmlApplicationContext]: aaa
[AbstractApplicationContext]: aaa
[BeanFactory]: aaa
[Environment]: aaa
[ListableBeanFactory]: aaa
[HierarchicalBeanFactory]: aaa
[EnvironmentCapable]: aaa
[ApplicationEventPublisher]: aaa
[MessageSource]: aaa
[ResourceLoader]: aaa
[ResourcePatternResolver]: aaa
[PathMatchingResourcePatternResolver]: aaa
[ApplicationContext]: aaa
[Lifecycle]: aaa
[ConfigurableApplicationContext]: aaa
[BeanFactoryPostProcessor]: aaa
[ApplicationListener]: aaa
[DefaultResourceLoader]: aaa
[AbstractRefreshableApplicationContext]: aaa
[AbstractRefreshableConfigApplicationContext]: aaa
[AbstractXmlApplicationContext]: aaa
[XmlBeanDefinitionReader]: aaa
[ClassPathXmlApplicationContext]: aaa
[XmlBeanDefinitionReader]: aaa
[BeanDefinitionReader]: aaa
[AbstractBeanDefinitionReader]: aaa
[NamespaceHandler]: aaa
[BeanDefinitionReaderUtils]: aaa