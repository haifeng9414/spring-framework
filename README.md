# 个人总结:

- [容器的实现]
    - [解析](#加载Bean配置信息)
    - [加载Bean配置信息](#加载Bean配置信息)
    - [单例bean的循环引用](#加载Bean配置信息)
    - [从容器获取bean](#从容器获取bean)

## 加载Bean配置信息

加载Bean的配置信息有很多中方法，如从XML解析、根据注解解析等，下面主要介绍根据XML文件解析Bean配置信息。
Spring中从XML文件解析Bean配置的信息的默认实现是[XmlBeanDefinitionReader]，该类的构造函数接收[BeanDefinitionRegistry]类为参数，
[BeanDefinitionRegistry]用于Bean信息的增删改查，默认实现是[DefaultListableBeanFactory]，最简单的Spring容器测试代码如下:
```java
DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
new XmlBeanDefinitionReader(factory).loadBeanDefinitions(PATH_TO_XML);
```

## 获取Bean

主要实现是[DefaultListableBeanFactory]类，[DefaultListableBeanFactory]的继承结构如下:
![DefaultListableBeanFactory继承结构图](img/DefaultListableBeanFactory.png)
[DefaultListableBeanFactory]实现了很多接口，每个接口都赋予了[DefaultListableBeanFactory]不同的职能，[AliasRegistry]接口包含了别名管理的相关方法，
[SimpleAliasRegistry]类利用Map实现了[AliasRegistry]接口并提供了循环别名检查，[BeanDefinitionRegistry]接口添加了[BeanDefinition]的注册方法，能将`beanName`关联到[BeanDefinition]，
[BeanFactory]接口定义了访问Spring容器的相关操作，如`getBean()`，`isSingleton()`等方法，接口上的注释说明了bean的生命周期里涉及到的接口和对应的方法:
1. BeanNameAware's setBeanName
1. BeanClassLoaderAware's setBeanClassLoader
1. BeanFactoryAware's setBeanFactory
1. EnvironmentAware's setEnvironment
1. EmbeddedValueResolverAware's setEmbeddedValueResolver
1. ResourceLoaderAware's setResourceLoader (only applicable when running in an application context)
1. ApplicationEventPublisherAware's setApplicationEventPublisher (only applicable when running in an application context)
1. MessageSourceAware's setMessageSource (only applicable when running in an application context)
1. ApplicationContextAware's setApplicationContext (only applicable when running in an application context)
1. ServletContextAware's setServletContext (only applicable when running in a web application context)
1. postProcessBeforeInitialization methods of BeanPostProcessors
1. InitializingBean's afterPropertiesSet
1. a custom init-method definition
1. postProcessAfterInitialization methods of BeanPostProcessors

`beanFactory`销毁时对应的接口和对应的方法
1. postProcessBeforeDestruction methods of DestructionAwareBeanPostProcessors
1. DisposableBean's destroy
1. a custom destroy-method definition

[SingletonBeanRegistry]接口定义了单例`beanFactory`的相关方法如`registerSingleton(String beanName, Object singletonObject)`、`getSingleton()`,
[DefaultSingletonBeanRegistry]类实现了基本的单例的注册功能，相当于是一个单例bean的`beanFactory`，并为[单例bean的循环引用](#单例bean的循环引用)提供了支持，
[FactoryBeanRegistrySupport]类增加了对[FactoryBean]的支持，能够从[FactoryBean]中获取Bean(主要方法是`getObjectFromFactoryBean`)，[HierarchicalBeanFactory]接口在BeanFactory基础上添加了两个方法`getParentBeanFactory`和`containsLocalBean`，
增加了`BeanFactory`的继承功能，[ConfigurableBeanFactory]添加了配置`BeanFactory`的方法，该接口主要在Spring内部配置`BeanFactory`时使用。[AbstractBeanFactory]类实现了[ConfigurableBeanFactory]接口的全部方法并继承了[FactoryBeanRegistrySupport]类，
提供了一些模版方法供子类实现(主要是`getBeanDefinition`和`createBean`)，[AutowireCapableBeanFactory]接口增加了创建bean、自动注入、初始化以及应用bean的后置处理器等相关方法，[AbstractAutowireCapableBeanFactory]类继承[AbstractBeanFactory]并实现了[AutowireCapableBeanFactory]接口，
[ListableBeanFactory]接口定义了获取bean配置清单的相关方法，[ConfigurableListableBeanFactory]接口添加了分析和修改bean definitions的方法，[DefaultListableBeanFactory]综合上面所有功能，主要是对bean注册后的处理。

[XmlBeanDefinitionReader]: aaa
[BeanDefinitionRegistry]: aaa
[DefaultListableBeanFactory]: aaa
[AliasRegistry]: aaa
[SimpleAliasRegistry]: aaa
[BeanDefinitionRegistry]: aaa
[BeanDefinition]: aaa
[BeanFactory]: aaa
[SingletonBeanRegistry]: aaa
[DefaultSingletonBeanRegistry]: aaa
[FactoryBeanRegistrySupport]: aaa
[FactoryBean]: aaa
[HierarchicalBeanFactory]: aaa
[ConfigurableBeanFactory]: aaa
[AbstractBeanFactory]: aaa
[AutowireCapableBeanFactory]: aaa
[AbstractAutowireCapableBeanFactory]: aaa

## 单例bean的循环引用

## 从容器获取bean

1. 