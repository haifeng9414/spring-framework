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

下面用[ClassPathXmlApplicationContext]举例说明[ApplicationContext]的工作过程，[ClassPathXmlApplicationContext]创建方式如下:

```java
ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext("xxxx.xml");
MyBeanA myBeanA = applicationContext.getBean("myBeanA", MyBeanA.class);
```

继承结构如下:
![ClassPathXmlApplicationContext继承结构图](../../img/ClassPathXmlApplicationContext.png)

下面分析解析XML文件并加载Bean的配置信息到容器的过程：

1. [ClassPathXmlApplicationContext]的构造函数根据传入的path创建[ClassPathResource]，保存到成员变量`configResources`中，[ClassPathXmlApplicationContext]重写了`getConfigResources()`方法，返回该成员变量，`getConfigResources()`方法返回值类型为[Resource]数组，表示当前[ApplicationContext]需要解析的资源，[Resource]接口可以返回资源的URL和File等对象。[ClassPathXmlApplicationContext]的构造函数在创建完[ClassPathResource]后调用`refresh()`方法刷新配置并解析XML，`refresh()`方法的实现在其父类[AbstractApplicationContext]
2. `refresh()`方法在进行解析之前先初始化容器状态，如设置容器启动时间，设置`closed`为false，之后初始化[PropertySources]并创建一个[DefaultListableBeanFactory]对象作为当前[ApplicationContext]的[BeanFactory]
3. 创建[DefaultListableBeanFactory]之后传入[DefaultListableBeanFactory]到`loadBeanDefinitions()`方法中加载XML配置，`loadBeanDefinitions()`方法在[AbstractXmlApplicationContext]类中实现，`loadBeanDefinitions()`方法首先以传入的[DefaultListableBeanFactory]作为[BeanDefinitionRegistry]对象来初始化[XmlBeanDefinitionReader]，并为[XmlBeanDefinitionReader]对象设置[EntityResolver]，[EntityResolver]接口的作用是提供一个寻找DTD或XSD声明的方式，默认情况下DTD或XSD声明会从网络下载，而网络下载很慢而且容易出错，所以Spring实现了寻找DTD或XSD声明的过程，返回本地已经保存好的DTD或XSD声明，具体实现可以看[ResourceEntityResolver]及其父类的注释。初始化完[XmlBeanDefinitionReader]对象后再调用`loadBeanDefinitions()`的重载方法，传入[XmlBeanDefinitionReader]对象开始解析XML并加载Bean定义
4. 重载的`loadBeanDefinitions()`方法调用`getConfigResources()`方法（第一步中[ClassPathXmlApplicationContext]对象实现了该方法）获取需要解析的[Resource]对象数组，之后传入[Resource]对象数组到[XmlBeanDefinitionReader]对象的`loadBeanDefinitions()`方法进行解析
5. [XmlBeanDefinitionReader]对象在解析[Resource]对象前将[Resource]对象对象封装为[EncodedResource]对象，[EncodedResource]对象支持指定encoding或charset读取[Resource]资源。之后[XmlBeanDefinitionReader]对象对象会根据[Resource]对象创建`Document`对象，这是`javax.xml`包下的类，专门用于解析XML文件。而对于`Document`到[BeanDefinition]的解析，[XmlBeanDefinitionReader]对象委托给了[BeanDefinitionDocumentReader]，[XmlBeanDefinitionReader]对象在`registerBeanDefinitions()`方法中创建[DefaultBeanDefinitionDocumentReader]对象并调用该对象的`registerBeanDefinitions()`方法注册[BeanDefinition]
6. [DefaultBeanDefinitionDocumentReader]对象的`registerBeanDefinitions()`方法首先调用`Document`的`getDocumentElement()`方法获取XML的`root element`，从`root element`开始XML的解析，解析时首先创建一个[BeanDefinitionParserDelegate]对象，该对象的作用是提供解析XML文件的工具方法，如`isDefaultNamespace()`方法用于判断当前XML文件是否是默认的命名空间，还提供了XML文件中不同元素的解析功能，如`import`、`alias`、`bean`等。[DefaultBeanDefinitionDocumentReader]对象利用[BeanDefinitionParserDelegate]对象获取XML的信息，再根据这些信息调用[BeanDefinitionParserDelegate]对象的不同方法解析XML文件。创建完[BeanDefinitionParserDelegate]对象后，[DefaultBeanDefinitionDocumentReader]对象调用`parseBeanDefinitions()`方法从`root element`开始解析XML
7. `parseBeanDefinitions()`方法获取`root element`的所有子元素并遍历，如果是默认命名空间的元素，如`<bean>`，则调用`parseDefaultElement()`方法解析，如果是自定义元素，如Spring自定义的`<tx:annotation-driven>`元素，则调用`parseCustomElement()`方法解析，对于自定义元素的使用，可以看[DefaultBeanDefinitionDocumentReader]类的`parseBeanDefinitions()`方法的注释。对于默认命名空间的解析，[DefaultBeanDefinitionDocumentReader]对象根据元素标签调用不同的方法，如`<bean>`元素调用`processBeanDefinition()`方法，而对于[BeanDefinition]的解析注册主要就在`processBeanDefinition()`方法，该方法首先调用[BeanDefinitionParserDelegate]对象的`parseBeanDefinitionElement()`方法获取[BeanDefinitionHolder]，该对象包含了bean的所有配置，如class、id、alias等，具体创建[BeanDefinitionHolder]对象的过程可以看的[BeanDefinitionParserDelegate]对象的`parseBeanDefinitionElement()`方法。创建[BeanDefinitionHolder]对象后根据就可以根据该对象获取[BeanDefinition]注册到[BeanDefinitionRegistry]，即[DefaultListableBeanFactory]，[DefaultListableBeanFactory]会以`beanName`为key，[BeanDefinition]为value，将[BeanDefinition]保存到Map中。

[BeanDefinitionRegistry]: aaa
[DefaultListableBeanFactory]: aaa
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