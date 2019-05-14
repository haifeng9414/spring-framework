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
   容器的`refresh()`方法可以被重复调用，所以需要判断[BeanFactory]是否已存在，存在则销毁。

3. Bean配置信息的加载在`loadBeanDefinitions()`方法中，该方法是个抽象方法，容器的实现可以重写该方法实现自己的Bean配置信息加载逻辑，[ClassPathXmlApplicationContext]用XML配置Bean的信息，该方法的实现在其父类[AbstractXmlApplicationContext]，代码：
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
   `Document`到[BeanDefinition]的解析，[XmlBeanDefinitionReader]对象委托给了[BeanDefinitionDocumentReader]，默认实现是[DefaultBeanDefinitionDocumentReader]，[XmlBeanDefinitionReader]调用该对象的`registerBeanDefinitions()`方法，传入`Document`对象注册[BeanDefinition]

8. `registerBeanDefinitions()`方法首先调用`Document`的`getDocumentElement()`方法获取XML的`root element`，从`root element`开始XML的解析，解析时首先创建一个[BeanDefinitionParserDelegate]对象，该对象的作用是提供解析XML文件的工具方法，如`isDefaultNamespace()`方法用于判断当前XML文件是否是默认的命名空间，还提供了XML文件中不同元素的解析功能，如`import`、`alias`、`bean`等。[DefaultBeanDefinitionDocumentReader]对象利用[BeanDefinitionParserDelegate]对象获取XML的信息，再根据这些信息调用[BeanDefinitionParserDelegate]对象的不同方法解析XML文件。[DefaultBeanDefinitionDocumentReader]创建完[BeanDefinitionParserDelegate]对象后，调用`parseBeanDefinitions()`方法从`root element`开始解析XML
9.  `parseBeanDefinitions()`方法获取`root element`的所有子元素并遍历，如果是默认命名空间的元素，如`<bean>`，则调用`parseDefaultElement()`方法解析，如果是自定义元素，如Spring自定义的`<tx:annotation-driven>`元素，则调用`parseCustomElement()`方法解析，对于自定义元素的使用，可以看[DefaultBeanDefinitionDocumentReader]类的`parseBeanDefinitions()`方法的注释。对于默认命名空间的解析，[DefaultBeanDefinitionDocumentReader]对象根据元素标签调用不同的方法，如`<bean>`元素调用`processBeanDefinition()`方法，`import`调用`importBeanDefinitionResource()`方法，而对于[BeanDefinition]的解析注册主要就在`processBeanDefinition()`方法，该方法首先调用[BeanDefinitionParserDelegate]对象的`parseBeanDefinitionElement()`方法获取[BeanDefinitionHolder]，该对象包含了bean的所有配置，如class、id、alias等，具体创建[BeanDefinitionHolder]对象的过程可以看的[BeanDefinitionParserDelegate]对象的`parseBeanDefinitionElement()`方法。创建[BeanDefinitionHolder]对象后就可以根据该对象获取[BeanDefinition]注册到[BeanDefinitionRegistry]，即[DefaultListableBeanFactory]，[DefaultListableBeanFactory]会以`beanName`为key，[BeanDefinition]为value，将[BeanDefinition]保存到Map中。

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